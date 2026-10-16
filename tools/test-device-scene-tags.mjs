import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// Runs only against a uniquely named, disposable local MySQL container.
// No host port, host database connection, bind mount or persistent volume.
const repository = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const migration = readFileSync(resolve(repository,
  'ruoyi-admin/src/main/resources/db/migration/V1_2_12__seed_device_scene_tags.sql'), 'utf8')
const bootstrap = readFileSync(resolve(repository, 'deploy/release/sql/init/10-ry-wvp.sql'), 'utf8')
const groups = [
  [
    "交通类",
    [
      "交通道路",
      "交通要道",
      "交通站点",
      "地铁站",
      "飞机场",
      "火车站",
      "汽车站",
      "码头",
      "桥隧",
      "街角",
      "十字路口",
      "人行道",
      "人行横道",
      "安检区",
      "候车大厅",
      "高速收费站",
      "卡口",
      "车辆出入口",
      "人员出入口",
      "公交站点"
    ]
  ],
  [
    "自然类",
    [
      "大型水面",
      "江河",
      "山丘",
      "森林",
      "山林",
      "野外灌木丛",
      "田野"
    ]
  ],
  [
    "乡村类",
    [
      "村庄"
    ]
  ],
  [
    "餐饮零售类",
    [
      "露天买卖市场",
      "餐饮场所",
      "室内商场",
      "商业门店"
    ]
  ],
  [
    "市政设施类",
    [
      "垃圾和垃圾箱",
      "加油站",
      "消防设施",
      "垂直电梯"
    ]
  ],
  [
    "公园广场类",
    [
      "公园",
      "露天活动广场"
    ]
  ],
  [
    "工程作业类",
    [
      "施工现场"
    ]
  ]
]
const expectedNames = new Set(groups.flatMap(([group, names]) => [group, ...names]))
const name = 'wvp-scene-tags-test-' + process.pid + '-' + Date.now()
const password = randomUUID()
const environment = { ...process.env, MYSQL_ROOT_PASSWORD: password, MYSQL_PWD: password, FLYWAY_PASSWORD: password }
let created = false
let checks = 0

function docker(args, input, allowFailure = false) {
  const result = spawnSync('docker', args, {
    input, encoding: 'utf8', env: environment, maxBuffer: 8 * 1024 * 1024
  })
  if (result.error) throw result.error
  if (result.status !== 0 && !allowFailure) {
    throw new Error('Docker test command failed: ' + (result.stderr || result.stdout))
  }
  return result
}
function sql(statement, database) {
  return docker(['exec', '-i', '-e', 'MYSQL_PWD', name, 'mysql',
    '--default-character-set=utf8mb4', '--batch', '--raw', '--skip-column-names',
    '-uroot', ...(database ? [database] : [])], statement).stdout.trim()
}
function rows(database) {
  return sql('SELECT id, category_type, parent_id, ancestors, category_name, sort_num, ' +
    "COALESCE(create_by,''), create_time, COALESCE(update_by,''), update_time " +
    'FROM wvp_device_category ORDER BY id;', database).split('\n')
    .filter(Boolean).map(line => line.split('\t'))
}
function snapshot(database) {
  return JSON.stringify([rows(database),
    sql('SELECT * FROM wvp_device_category_relation ORDER BY id;', database)])
}
function check(label, action) {
  action()
  checks++
  console.log('PASS ' + label)
}
function bootstrapClassificationSchema(database) {
  // Extract only the authoritative schema, never bootstrap user/device rows.
  for (const table of ['wvp_device_category', 'wvp_device_category_relation']) {
    const start = bootstrap.indexOf('CREATE TABLE `' + table + '`')
    assert.ok(start >= 0, 'Bootstrap table schema exists: ' + table)
    const end = bootstrap.indexOf(';', start)
    assert.ok(end > start)
    sql(bootstrap.slice(start, end + 1), database)
  }
}
function flyway(database, command) {
  const runner = name + '-flyway-' + randomUUID().slice(0, 8)
  let runnerCreated = false
  try {
    docker(['create', '--pull=never', '--name', runner, '--label', 'vlstream.test=scene-tags',
      '--network=container:' + name, '-e', 'FLYWAY_PASSWORD', 'flyway/flyway:8.5.13',
      '-url=jdbc:mysql://127.0.0.1:3306/' + database + '?allowPublicKeyRetrieval=true&useSSL=false',
      '-user=root', '-baselineOnMigrate=true', '-baselineVersion=1.2.11',
      '-locations=filesystem:/flyway/sql', command])
    runnerCreated = true
    docker(['cp', resolve(repository,
      'ruoyi-admin/src/main/resources/db/migration/V1_2_12__seed_device_scene_tags.sql'),
      runner + ':/flyway/sql/V1_2_12__seed_device_scene_tags.sql'])
    docker(['start', '--attach', runner])
    const exitCode = docker(['inspect', '--format', '{{.State.ExitCode}}', runner]).stdout.trim()
    assert.equal(exitCode, '0', 'Flyway ' + command + ' succeeds')
  } finally {
    if (runnerCreated) {
      const removed = docker(['rm', '--force', '--volumes', runner], undefined, true)
      if (removed.status !== 0) throw new Error('Could not remove Flyway test container: ' + runner)
    }
  }
}
function verifyDefaultBranches(database) {
  const categories = rows(database).filter(row => row[1] === 'TAG')
  for (const [group, names] of groups) {
    const roots = categories.filter(row => row[2] === '0' && row[4] === group)
    assert.equal(roots.length, 1, 'One default root for ' + group)
    const parent = roots[0]
    for (const name of names) {
      const matching = categories.filter(row => row[2] === parent[0] && row[4] === name)
      assert.equal(matching.length, 1, 'One default leaf at ' + group + '/' + name)
      assert.equal(matching[0][3], parent[3] + ',' + parent[0])
    }
  }
}
function verifyDefaults(database) {
  const categories = rows(database)
  assert.equal(categories.length, 46)
  assert.deepEqual(new Set(categories.map(row => row[4])), expectedNames)
  assert.equal(categories.filter(row => row[2] === '0').length, 7)
  for (const [group, names] of groups) {
    const parent = categories.find(row => row[4] === group)
    assert.equal(parent[1], 'TAG')
    assert.equal(parent[2], '0')
    assert.equal(parent[3], '0')
    const children = categories.filter(row => row[2] === parent[0])
      .sort((left, right) => Number(left[5]) - Number(right[5]))
    assert.deepEqual(children.map(row => row[4]), names)
    assert.ok(children.every(row => row[1] === 'TAG' && row[3] === '0,' + parent[0]))
  }
  assert.equal(sql('SELECT COUNT(*) FROM wvp_device_category_relation;', database), '0')
}

try {
  const context = docker(['context', 'show']).stdout.trim()
  const endpoint = docker(['context', 'inspect', context,
    '--format', '{{.Endpoints.docker.Host}}']).stdout.trim()
  assert.ok(endpoint.startsWith('npipe://') || endpoint === 'unix:///var/run/docker.sock',
    'Tests require a local Docker endpoint')
  docker(['run', '--detach', '--pull=never', '--name', name,
    '--label', 'vlstream.test=scene-tags', '--network=none',
    '--tmpfs', '/var/lib/mysql', '--tmpfs', '/tmp', '-e', 'MYSQL_ROOT_PASSWORD',
    process.env.WVP_SCENE_TAG_TEST_IMAGE || 'mysql:8.0.36'])
  created = true
  let ready = false
  for (let attempt = 0; attempt < 60; attempt++) {
    const ping = docker(['exec', '-e', 'MYSQL_PWD', name, 'mysql',
      '-uroot', '-Nse', 'SELECT 1'], undefined, true)
    if (ping.status === 0) { ready = true; break }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
  assert.ok(ready, 'Disposable MySQL becomes ready')

  sql('CREATE DATABASE scene_fresh CHARACTER SET utf8mb4; ' +
      'CREATE DATABASE scene_bootstrap CHARACTER SET utf8mb4; ' +
      'CREATE DATABASE scene_upgrade CHARACTER SET utf8mb4; ' +
      'CREATE DATABASE scene_legacy CHARACTER SET utf8mb4; ' +
      'CREATE DATABASE scene_nested_only CHARACTER SET utf8mb4; ' +
      'CREATE DATABASE scene_flyway_fresh CHARACTER SET utf8mb4; ' +
      'CREATE DATABASE scene_flyway_upgrade CHARACTER SET utf8mb4;')
  sql(migration, 'scene_fresh')
  check('fresh schema: exact 7 groups, 39 names, ancestry and screenshot order', () => verifyDefaults('scene_fresh'))
  const first = snapshot('scene_fresh')
  sql(migration, 'scene_fresh')
  check('repeat execution leaves all IDs, fields, timestamps and relations unchanged',
    () => assert.equal(snapshot('scene_fresh'), first))

  bootstrapClassificationSchema('scene_bootstrap')
  sql(migration, 'scene_bootstrap')
  check('release bootstrap and empty-schema installation produce the same catalogue', () => {
    verifyDefaults('scene_bootstrap')
    assert.deepEqual(rows('scene_bootstrap').map(row => row.slice(1, 6)),
      rows('scene_fresh').map(row => row.slice(1, 6)))
  })

  bootstrapClassificationSchema('scene_upgrade')
  sql(`INSERT INTO wvp_device_category
      (id,category_type,parent_id,ancestors,category_name,sort_num,create_by,create_time,update_by,update_time)
    VALUES
      (400,'TAG',0,'0','自定义场景',123,'owner','2020-01-01','owner','2020-01-02'),
      (500,'TAG',0,'0','交通类',777,'owner','2020-01-01','owner','2020-01-02'),
      (550,'TAG',500,'0,500','交通道路',654,'owner','2020-01-01','owner','2020-01-02'),
      (600,'TAG',400,'0,400','地铁站',321,'owner','2020-01-01','owner','2020-01-02'),
      (700,'REGION',0,'0','村庄',432,'owner','2020-01-01','owner','2020-01-02'),
      (800,'GROUP',0,'0','公园',543,'owner','2020-01-01','owner','2020-01-02');
    INSERT INTO wvp_device_category_relation
      (id,category_id,category_type,protocol_type,device_key,create_by,create_time)
      VALUES (1,600,'TAG','VLSTREAM','fixture-device','owner','2020-01-01');`, 'scene_upgrade')
  const customRows = rows('scene_upgrade')
  const customRelations = sql('SELECT * FROM wvp_device_category_relation;', 'scene_upgrade')
  sql(migration, 'scene_upgrade')
  check('upgrade preserves custom IDs, names, parents, sorting, authors and timestamps', () => {
    assert.deepEqual(rows('scene_upgrade').filter(row => Number(row[0]) <= 800), customRows)
    assert.equal(sql('SELECT * FROM wvp_device_category_relation;', 'scene_upgrade'), customRelations)
  })
  check('same-named user leaf remains intact while the correct default-parent leaf is inserted', () => {
    const categories = rows('scene_upgrade')
    const metro = categories.filter(row => row[1] === 'TAG' && row[4] === '地铁站')
    assert.equal(metro.length, 2)
    assert.deepEqual(new Set(metro.map(row => row[2])), new Set(['400', '500']))
    const defaultMetro = metro.find(row => row[2] === '500')
    assert.equal(defaultMetro[3], '0,500')
    assert.equal(categories.filter(row => row[1] === 'TAG').length, 48)
  })
  check('same-parent default leaf reuses its real ID and preserves custom sorting', () => {
    const road = rows('scene_upgrade').find(row => row[1] === 'TAG' && row[2] === '500' && row[4] === '交通道路')
    assert.equal(road[0], '550')
    assert.equal(road[3], '0,500')
    assert.equal(road[5], '654')
  })
  check('partial/custom upgrade contains every scene at its expected default path', () =>
    verifyDefaultBranches('scene_upgrade'))
  check('REGION/GROUP names do not suppress the required TAG names', () => {
    const categories = rows('scene_upgrade')
    assert.equal(categories.filter(row => row[1] === 'TAG' && row[4] === '村庄').length, 1)
    assert.equal(categories.filter(row => row[1] === 'TAG' && row[4] === '公园').length, 1)
  })
  const upgraded = snapshot('scene_upgrade')
  sql(migration, 'scene_upgrade')
  check('partial/custom upgrade remains idempotent', () => assert.equal(snapshot('scene_upgrade'), upgraded))

  bootstrapClassificationSchema('scene_legacy')
  sql(`INSERT INTO wvp_device_category (id,category_type,parent_id,ancestors,category_name,sort_num)
    VALUES (1000,'TAG',0,'0','用户分组',8),
           (1100,'TAG',1000,'0,1000','自然类',99),
           (1200,'TAG',0,'0','自然类',88),
           (1300,'TAG',1000,'0,1000','森林',77);`, 'scene_legacy')
  const legacy = rows('scene_legacy')
  sql(migration, 'scene_legacy')
  check('root group ID 1200 wins over lower nested same-name ID 1100 without changing user data', () => {
    const categories = rows('scene_legacy')
    assert.deepEqual(categories.filter(row => Number(row[0]) <= 1300), legacy)
    const river = categories.find(row => row[4] === '江河')
    assert.equal(river[2], '1200')
    assert.equal(river[3], '0,1200')
    assert.equal(categories.filter(row => row[2] === '1100').length, 0)
    verifyDefaultBranches('scene_legacy')
    assert.equal(sql('SELECT COUNT(*) FROM wvp_device_category_relation;', 'scene_legacy'), '0')
  })
  check('foreign-parent Forest is preserved and does not suppress Nature/Forest', () => {
    const forests = rows('scene_legacy').filter(row => row[1] === 'TAG' && row[4] === '森林')
    assert.equal(forests.length, 2)
    assert.deepEqual(new Set(forests.map(row => row[2])), new Set(['1000', '1200']))
    assert.equal(forests.find(row => row[2] === '1200')[3], '0,1200')
  })
  const legacyAfter = snapshot('scene_legacy')
  sql(migration, 'scene_legacy')
  check('both review collision fixtures remain idempotent', () =>
    assert.equal(snapshot('scene_legacy'), legacyAfter))

  bootstrapClassificationSchema('scene_nested_only')
  sql(`INSERT INTO wvp_device_category (id,category_type,parent_id,ancestors,category_name,sort_num)
    VALUES (2000,'TAG',0,'0','用户分组',8),
           (2100,'TAG',2000,'0,2000','自然类',99),
           (2200,'TAG',2100,'0,2000,2100','森林',77);`, 'scene_nested_only')
  const nestedBefore = rows('scene_nested_only')
  sql(migration, 'scene_nested_only')
  check('a nested same-name group alone cannot suppress creation of the required root group', () => {
    const categories = rows('scene_nested_only')
    assert.deepEqual(categories.filter(row => Number(row[0]) <= 2200), nestedBefore)
    const root = categories.find(row => row[1] === 'TAG' && row[2] === '0' && row[4] === '自然类')
    assert.ok(root)
    assert.notEqual(root[0], '2100')
    assert.equal(categories.filter(row => row[2] === '2100').length, 1)
    verifyDefaultBranches('scene_nested_only')
  })
  const nestedAfter = snapshot('scene_nested_only')
  sql(migration, 'scene_nested_only')
  check('nested-only group collision remains idempotent', () =>
    assert.equal(snapshot('scene_nested_only'), nestedAfter))

  flyway('scene_flyway_fresh', 'migrate')
  flyway('scene_flyway_fresh', 'validate')
  check('Flyway parses and applies the actual migration on an empty installation', () => {
    verifyDefaults('scene_flyway_fresh')
    assert.equal(sql("SELECT version FROM flyway_schema_history WHERE type='SQL' AND success=1;",
      'scene_flyway_fresh'), '1.2.12')
  })
  bootstrapClassificationSchema('scene_flyway_upgrade')
  flyway('scene_flyway_upgrade', 'migrate')
  const flywayUpgrade = snapshot('scene_flyway_upgrade')
  flyway('scene_flyway_upgrade', 'migrate')
  check('Flyway baselines an existing installation and skips the completed migration on restart', () => {
    verifyDefaults('scene_flyway_upgrade')
    assert.equal(snapshot('scene_flyway_upgrade'), flywayUpgrade)
    assert.equal(sql("SELECT GROUP_CONCAT(version ORDER BY installed_rank) FROM flyway_schema_history WHERE success=1;",
      'scene_flyway_upgrade'), '1.2.11,1.2.12')
  })
  console.log('Passed ' + checks + ' isolated MySQL migration checks.')
} finally {
  if (created) {
    const cleanup = docker(['rm', '--force', '--volumes', name], undefined, true)
    if (cleanup.status !== 0) throw new Error('Could not remove disposable test container: ' + name)
    console.log('Removed disposable test container.')
  }
}

