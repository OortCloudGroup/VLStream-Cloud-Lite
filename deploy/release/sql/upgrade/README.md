# Upgrade SQL

Do not run the fresh-install script against an existing database. Database
upgrades are delivered as immutable Flyway migrations in
`ruoyi-admin/src/main/resources/db/migration/` and run automatically when the
WVP backend starts. This directory is intentionally present in the release
package for operator visibility; it contains no manually executable migration
for v1.0.0.

## GAT1400 menu disablement (migration 1.2.10)

`V1_2_10__disable_unavailable_gat1400_menus.sql` disables the legacy GAT1400
directory and page entries because Lite does not provide their backend APIs.
It matches route/component paths rather than installation-specific menu IDs.
Menu records, role assignments, visibility flags and business data are retained.
Databases without these entries are unchanged. The legacy `sql/ry-wvp.sql`
bootstrap also seeds these entries as disabled; the release bootstrap already
omits them.

This takes effect when the upgraded backend runs Flyway at startup. Refresh the
browser or sign in again to fetch the updated routes. To restore the feature
after its backend is implemented, add a new migration enabling the intended
entries; do not edit this applied migration.
