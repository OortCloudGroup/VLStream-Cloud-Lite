<template>
  <div class="app-container workbench-page">
    <el-tabs v-model="activeName" class="work-tabs" @tab-click="handleClick">
      <el-tab-pane label="GB2818" name="GB"></el-tab-pane>
      <el-tab-pane label="ONVIF" name="ONVIF"></el-tab-pane>
      <el-tab-pane label="RTSP" name="RTSP"></el-tab-pane>
      <el-tab-pane label="ISUP" name="ISUP"></el-tab-pane>
      <el-tab-pane :label="$tp('大华')" name="DAHUA"></el-tab-pane>
    </el-tabs>

    <div class="workbench-layout">
      <aside v-yResize class="workbench-aside">
        <div class="aside-header">
          <div class="aside-title">{{ $tp("设备列表") }}</div>
          <div class="aside-actions">
            <el-button type="primary" link @click="handleSave">{{ $tp("保存") }}</el-button>
            <el-button type="danger" link @click="handleCleanUp">{{ $tp("清除") }}</el-button>
          </div>
        </div>

        <div v-show="activeName === 'GB'" class="aside-body">
              <div class="head-container">
                <el-input v-model="deviceName" :placeholder="$tp('搜索设备名称')" clearable prefix-icon="Search"
                          style="margin-bottom: 20px"/>
              </div>
              <div class="top">
                <div>{{ $tp("通道列表") }}</div>
                <div>
                  <el-switch
                      v-model="activeValue"
                      :active-text="$tp('行政区划')"
                      :inactive-text="$tp('业务分组')"
                      @change="onSwitch"
                  />
                </div>
              </div>
              <div class="tree">
                <el-tree
                    ref="favoritesTreeRef"
                    :data="treeFavoriteData"
                    :props="defaultProps"
                    lazy
                    :load="loadFavoriteNode"
                    @node-click="handleNodeFavoriteClick"
                    :expand-on-click-node="false"
                ></el-tree>
              </div>
              <div class="tree">
                <el-tree
                    ref="markTreeRef"
                    :data="treeMarkData"
                    :props="defaultProps"
                    lazy
                    :load="loadMarkNode"
                    @node-click="handleNodeMarkClick"
                    :expand-on-click-node="false"
                ></el-tree>
              </div>
              <div class="tree">
                <el-tree
                    style="height: 440px;"
                    v-if="activeValue"
                    ref="deviceTreeRef"
                    :data="treeData"
                    :props="defaultProps"
                    node-key="gbId"
                    lazy
                    :load="loadNode"
                    @node-click="handleNodeClick"
                    :expand-on-click-node="false"
                    :filter-node-method="filterNode"
                    @node-contextmenu="handleRightClick"
                >
                  <template #default="{ node, data }">
                    <div class="custom-tree-node">
                      <div v-if="!data.dataType">{{ node.label }}</div>
                      <div v-if="data.dataType" style="display:flex;">
                        <svg-icon
                            :icon-class="data.iconClass || 'work-camera-1'"
                            style="margin-right: 6px"
                        />
                        <span
                            style="width: 100px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;"
                            :style="{ color: data.iconClass === 'work-camera-2' ? '#409EFF' :
                            data.iconClass === 'work-camera-3' ? '#67C23A' : 'inherit' }"
                        >{{ node.label }}
                        </span>

                      </div>
                      <div v-if="data.dataType" style="display:flex;"
                           :style="{ color: data.iconClass === 'work-camera-2' ? '#409EFF' :
                            data.iconClass === 'work-camera-3' ? '#67C23A' : 'inherit' }">
                        <div>
                          {{ data.gbIpAddress }}
                        </div>
                        <div style="margin-left: 6px" v-if="data.dataType === 1">
                          <el-icon v-if="data.gbStatus === 'ON'" color="#67C23A">
                            <CircleCheckFilled/>
                          </el-icon>
                          <el-icon v-if="data.gbStatus !== 'ON'" color="#F56C6C">
                            <WarningFilled/>
                          </el-icon>
                        </div>
                      </div>
                    </div>
                  </template>
                </el-tree>

                <el-tree
                    style="height: 440px;"
                    v-if="!activeValue"
                    ref="deviceTreeRef"
                    :data="treeData"
                    :props="defaultProps"
                    node-key="gbId"
                    lazy
                    :load="groupLoadNode"
                    @node-click="handleNodeClick"
                    :expand-on-click-node="false"
                    :filter-node-method="filterNode"
                    :highlight-current="true"
                    @node-contextmenu="handleRightClick"
                >
                  <template #default="{ node, data }">
                    <div class="custom-tree-node">
                      <div v-if="!data.dataType">{{ node.label }}</div>
                      <div v-if="data.dataType" style="display:flex;">
                        <svg-icon
                            :icon-class="data.iconClass || 'work-camera-1'"
                            style="margin-right: 6px"
                        />
                        <div :style="{ color: data.iconClass === 'work-camera-2' ? '#409EFF' :
                            data.iconClass === 'work-camera-3' ? '#67C23A' : 'inherit' }">
                          {{ node.label }}
                        </div>
                      </div>
                      <div v-if="data.dataType" style="display:flex;">
                        <div :style="{ color: data.iconClass === 'work-camera-2' ? '#409EFF' :
                            data.iconClass === 'work-camera-3' ? '#67C23A' : 'inherit' }">
                          {{ data.gbIpAddress }}
                        </div>
                        <div style="margin-left: 6px">
                          <el-icon v-if="data.gbStatus === 'ON'" color="#67C23A">
                            <CircleCheckFilled/>
                          </el-icon>
                          <el-icon v-if="data.gbStatus !== 'ON'" color="#F56C6C">
                            <WarningFilled/>
                          </el-icon>
                        </div>
                      </div>
                    </div>
                  </template>
                </el-tree>
              </div>
              <el-divider/>
              <div style="display: flex; justify-content: center">
                <div style="display: grid; grid-template-columns: 240px auto; height: 180px; overflow: auto">
                  <!-- 左侧控制区域 -->
                  <div style="display: grid; grid-template-columns: 100px auto;">
                    <!-- 方向控制 -->
                    <div class="control-wrapper">
                      <div class="control-btn control-top" @mousedown="ptzCamera('up')" @mouseup="ptzCamera('stop')">
                        <el-icon class="icon">
                          <CaretTop/>
                        </el-icon>
                        <div class="control-inner-btn control-inner"></div>
                      </div>
                      <div class="control-btn control-left" @mousedown="ptzCamera('left')" @mouseup="ptzCamera('stop')">
                        <el-icon class="icon">
                          <CaretLeft/>
                        </el-icon>
                        <div class="control-inner-btn control-inner"></div>
                      </div>
                      <div class="control-btn control-bottom" @mousedown="ptzCamera('down')"
                           @mouseup="ptzCamera('stop')">
                        <el-icon class="icon">
                          <CaretBottom/>
                        </el-icon>
                        <div class="control-inner-btn control-inner"></div>
                      </div>
                      <div class="control-btn control-right" @mousedown="ptzCamera('right')"
                           @mouseup="ptzCamera('stop')">
                        <el-icon class="icon">
                          <CaretRight/>
                        </el-icon>
                        <div class="control-inner-btn control-inner"></div>
                      </div>
                      <div class="control-round">
                        <div class="control-round-inner"><i class="fa fa-pause-circle"></i></div>
                      </div>
                      <!-- 速度控制 -->
                      <div class="contro-speed" style="position: absolute; left: 4px; top: 112px; width: 100px;">
                        <el-slider v-model="controSpeed" :min="1"></el-slider>
                      </div>
                    </div>
                    <!-- 变倍、聚焦、光圈控制 -->
                    <div>
                      <div class="ptz-btn-box">
                        <div @mousedown="ptzCamera('zoomin')" @mouseup="ptzCamera('stop')" :title="$tp('变倍+')">
                          <el-icon class="control-zoom-btn" style="font-size: 24px;">
                            <ZoomIn/>
                          </el-icon>
                        </div>
                        <div @mousedown="ptzCamera('zoomout')" @mouseup="ptzCamera('stop')" :title="$tp('变倍-')">
                          <el-icon class="control-zoom-btn" style="font-size: 24px;">
                            <ZoomOut/>
                          </el-icon>
                        </div>
                      </div>
                      <div class="ptz-btn-box">
                        <div @mousedown="focusCamera('near')" @mouseup="focusCamera('stop')" :title="$tp('聚焦+')">
                          <i class="iconfont icon-bianjiao-fangda control-zoom-btn" style="font-size: 24px;"></i>
                        </div>
                        <div @mousedown="focusCamera('far')" @mouseup="focusCamera('stop')" :title="$tp('聚焦-')">
                          <i class="iconfont icon-bianjiao-suoxiao control-zoom-btn" style="font-size: 24px;"></i>
                        </div>
                      </div>
                      <div class="ptz-btn-box">
                        <div @mousedown="irisCamera('in')" @mouseup="irisCamera('stop')" :title="$tp('光圈+')">
                          <i class="iconfont icon-guangquan control-zoom-btn" style="font-size: 24px;"></i>
                        </div>
                        <div @mousedown="irisCamera('out')" @mouseup="irisCamera('stop')" :title="$tp('光圈-')">
                          <i class="iconfont icon-guangquan- control-zoom-btn" style="font-size: 24px;"></i>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
        </div>
        <div v-show="activeName === 'ONVIF'" class="aside-body">
              <div class="head-container">
                <el-input v-model="deviceName" :placeholder="$tp('搜索设备名称')" clearable prefix-icon="Search"
                          style="margin-bottom: 20px" @change="deviceChange"/>
              </div>

              <div v-if="listDevice.length >0">
                <InfiniteList
                    v-if="listDevice.length >0"
                    :data="listDevice"
                    :width="'100%'"
                    :height="'470px'"
                    :itemSize="40"
                    v-slot="{ item, index }"
                >
                  <div style="cursor: pointer">
                    <el-tag @click="deviceClick(item)" style="width: 100%;" size="large"
                            :type="selectDeviceId === item.id ? 'success' : ''">
                      <svg-icon icon-class="camera" style="margin-right: 6px"/>
                      {{ item.name }}
                    </el-tag>
                  </div>
                </InfiniteList>
                <el-divider/>
                <div v-if="checkPermi(['dahua:device:ptzCtrl'])" style="display: flex;justify-content: center">
                  <div style="display: grid; grid-template-columns: 240px auto; height: 180px; overflow: auto">
                    <!-- 左侧控制区域 -->
                    <div style="display: grid; grid-template-columns: 100px auto;">
                      <!-- 方向控制 -->
                      <div class="control-wrapper">
                        <div class="control-btn control-top" @mousedown="onvifPtzCtrlStartFun('upper')"
                             @mouseup="onvifPtzCtrlEndFun()">
                          <el-icon class="icon">
                            <CaretTop/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-left" @mousedown="onvifPtzCtrlStartFun('left')"
                             @mouseup="onvifPtzCtrlEndFun()">
                          <el-icon class="icon">
                            <CaretLeft/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-bottom" @mousedown="onvifPtzCtrlStartFun('below')"
                             @mouseup="onvifPtzCtrlEndFun()">
                          <el-icon class="icon">
                            <CaretBottom/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-right" @mousedown="onvifPtzCtrlStartFun('right')"
                             @mouseup="onvifPtzCtrlEndFun()">
                          <el-icon class="icon">
                            <CaretRight/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-round">
                          <div class="control-round-inner"><i class="fa fa-pause-circle"></i></div>
                        </div>
                        <!-- 速度控制 -->
                        <div class="contro-speed" style="position: absolute; left: 4px; top: 112px; width: 100px;">
                          <el-slider v-model="onvifControSpeed" :step="0.1" :min="0.1" :max="1"></el-slider>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <el-empty v-if="listDevice.length === 0" :image-size="50" :description="$tp('暂无数据')"/>
        </div>
        <div v-show="activeName === 'RTSP'" class="aside-body">
              <div class="head-container">
                <el-input v-model="deviceName" :placeholder="$tp('搜索设备名称')" clearable prefix-icon="Search"
                          style="margin-bottom: 20px" @change="deviceChange"/>
              </div>
              <InfiniteList
                  v-if="listDevice.length >0"
                  :data="listDevice"
                  :width="'100%'"
                  :height="'700px'"
                  :itemSize="40"
                  v-slot="{ item, index }"
              >
                <div style="cursor: pointer">
                  <el-tag @click="deviceClick(item)" style="width: 100%;" size="large"
                          :type="selectDeviceId === item.id ? 'success' : ''">
                    <svg-icon icon-class="camera" style="margin-right: 6px"/>
                    {{ item.name }}
                  </el-tag>
                </div>
              </InfiniteList>

              <el-empty v-if="listDevice.length === 0" :image-size="50" :description="$tp('暂无数据')"/>
        </div>
        <div v-show="activeName === 'ISUP'" class="aside-body">
              <div class="head-container">
                <el-input v-model="deviceName" :placeholder="$tp('搜索设备名称')" clearable prefix-icon="Search"
                          style="margin-bottom: 20px" @change="deviceChange"/>
              </div>

              <div v-if="listDevice.length >0">
                <InfiniteList
                    v-if="listDevice.length >0"
                    :data="listDevice"
                    :width="'100%'"
                    :height="'470px'"
                    :itemSize="40"
                    v-slot="{ item, index }"
                >
                  <div style="cursor: pointer">
                    <el-tag @click="deviceClick(item)" style="width: 100%;" size="large"
                            :type="selectDeviceId === item.deviceId ? 'success' : ''">
                      <svg-icon icon-class="camera" style="margin-right: 6px"/>
                      {{ item.name }}
                    </el-tag>
                  </div>
                </InfiniteList>
                <el-divider/>
                <div v-if="checkPermi(['isup:lsupDevice:ptzCtrl'])" style="display: flex;justify-content: center">
                  <div style="display: grid; height: 180px; ">
                    <!-- 左侧控制区域 -->
                    <div style="display: grid; grid-template-columns: 100px auto;">
                      <!-- 方向控制 -->
                      <div class="control-wrapper">
                        <div class="control-btn control-top" @mousedown="ptzCtrlStartFun(3)" @mouseup="ptzCtrlEndFun()">
                          <el-icon class="icon">
                            <CaretTop/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-left" @mousedown="ptzCtrlStartFun(2)"
                             @mouseup="ptzCtrlEndFun()">
                          <el-icon class="icon">
                            <CaretLeft/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-bottom" @mousedown="ptzCtrlStartFun(4)"
                             @mouseup="ptzCtrlEndFun()">
                          <el-icon class="icon">
                            <CaretBottom/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-right" @mousedown="ptzCtrlStartFun(1)"
                             @mouseup="ptzCtrlEndFun()">
                          <el-icon class="icon">
                            <CaretRight/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-round">
                          <div class="control-round-inner"><i class="fa fa-pause-circle"></i></div>
                        </div>
                        <!-- 速度控制 -->
                        <div class="contro-speed" style="position: absolute; left: 4px; top: 112px; width: 100px;">
                          <el-slider v-model="haikangControSpeed" :min="1" :max="15"></el-slider>
                        </div>
                      </div>

                      <!-- 变倍、聚焦、光圈控制 -->
                      <div>
                        <div style="margin-left: 20px;width: 100px;">
                          {{ $tp("聚焦") }}
                          <el-slider v-model="haikangControSpeedFocus" :max="100" :min="-100"
                                     @change="haikangFocusCamera"/>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>


              <el-empty v-if="listDevice.length === 0" :image-size="50" :description="$tp('暂无数据')"/>
        </div>
        <div v-show="activeName === 'DAHUA'" class="aside-body">
              <div class="head-container">
                <el-input v-model="deviceName" :placeholder="$tp('搜索设备名称')" clearable prefix-icon="Search"
                          style="margin-bottom: 20px" @change="deviceChange"/>
              </div>

              <div v-if="listDevice.length >0">
                <InfiniteList
                    :data="listDevice"
                    :width="'100%'"
                    :height="'470px'"
                    :itemSize="40"
                    v-slot="{ item, index }"
                >
                  <div style="cursor: pointer">
                    <el-tag @click="deviceClick(item)" style="width: 100%;" size="large"
                            :type="selectDeviceId === item.id ? 'success' : ''">
                      <svg-icon icon-class="camera" style="margin-right: 6px"/>
                      {{ item.name }}
                    </el-tag>
                  </div>
                </InfiniteList>
                <el-divider/>
                <div v-if="checkPermi(['dahua:device:ptzCtrl'])" style="display: flex;justify-content: center">
                  <div style="display: grid; grid-template-columns: 240px auto; height: 180px; overflow: auto">
                    <!-- 左侧控制区域 -->
                    <div style="display: grid; grid-template-columns: 100px auto;">
                      <!-- 方向控制 -->
                      <div class="control-wrapper">
                        <div class="control-btn control-top" @mousedown="ptzControlUpStartFun('up')"
                             @mouseup="ptzControlUpEndFun('up')">
                          <el-icon class="icon">
                            <CaretTop/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-left" @mousedown="ptzControlUpStartFun('left')"
                             @mouseup="ptzControlUpEndFun('left')">
                          <el-icon class="icon">
                            <CaretLeft/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-bottom" @mousedown="ptzControlUpStartFun('down')"
                             @mouseup="ptzControlUpEndFun('down')">
                          <el-icon class="icon">
                            <CaretBottom/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-btn control-right" @mousedown="ptzControlUpStartFun('right')"
                             @mouseup="ptzControlUpEndFun('right')">
                          <el-icon class="icon">
                            <CaretRight/>
                          </el-icon>
                          <div class="control-inner-btn control-inner"></div>
                        </div>
                        <div class="control-round">
                          <div class="control-round-inner"><i class="fa fa-pause-circle"></i></div>
                        </div>
                        <!-- 速度控制 -->
                        <div class="contro-speed" style="position: absolute; left: 4px; top: 112px; width: 100px;">
                          <el-slider v-model="dahuaControSpeed" :min="1" :max="15"></el-slider>
                        </div>
                      </div>

                      <!-- 变倍、聚焦、光圈控制 -->
                      <div>
                        <div class="ptz-btn-box">
                          <div @mousedown="ptzControlUpStartFun('doubling+')" @mouseup="ptzControlUpEndFun('doubling+')"
                               :title="$tp('变倍+')">
                            <el-icon class="control-zoom-btn" style="font-size: 24px;">
                              <ZoomIn/>
                            </el-icon>
                          </div>
                          <div @mousedown="ptzControlUpStartFun('doubling-')" @mouseup="ptzControlUpEndFun('doubling-')"
                               :title="$tp('变倍-')">
                            <el-icon class="control-zoom-btn" style="font-size: 24px;">
                              <ZoomOut/>
                            </el-icon>
                          </div>
                        </div>
                        <div class="ptz-btn-box">
                          <div @mousedown="ptzControlUpStartFun('zoom+')" @mouseup="ptzControlUpEndFun('zoom+')"
                               :title="$tp('聚焦+')">
                            <i class="iconfont icon-bianjiao-fangda control-zoom-btn" style="font-size: 24px;"></i>
                          </div>
                          <div @mousedown="ptzControlUpStartFun('zoom-')" @mouseup="ptzControlUpEndFun('zoom-')"
                               :title="$tp('聚焦-')">
                            <i class="iconfont icon-bianjiao-suoxiao control-zoom-btn" style="font-size: 24px;"></i>
                          </div>
                        </div>
                        <div class="ptz-btn-box">
                          <div @mousedown="ptzControlUpStartFun('aperture+')" @mouseup="ptzControlUpEndFun('aperture+')"
                               :title="$tp('光圈+')">
                            <i class="iconfont icon-guangquan control-zoom-btn" style="font-size: 24px;"></i>
                          </div>
                          <div @mousedown="ptzControlUpStartFun('aperture-')" @mouseup="ptzControlUpEndFun('aperture-')"
                               :title="$tp('光圈-')">
                            <i class="iconfont icon-guangquan- control-zoom-btn" style="font-size: 24px;"></i>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <el-empty v-if="listDevice.length === 0" :image-size="50" :description="$tp('暂无数据')"/>
        </div>
      </aside>

      <main class="workbench-main">
        <div class="workbench-toolbar">
          <svg-icon :class="['flex-icon', { active: model === 1 }]" icon-class="screen1" @click="spiltIndex(1)" />
          <svg-icon :class="['flex-icon', { active: model === 4 }]" icon-class="screen4" @click="spiltIndex(4)" />
          <svg-icon :class="['flex-icon', { active: model === 6 }]" icon-class="screen6" @click="spiltIndex(6)" />
          <svg-icon :class="['flex-icon', { active: model === 9 }]" icon-class="screen9" @click="spiltIndex(9)" />
          <el-dropdown trigger="click" @command="handleScreenMore">
            <span class="flex-icon more-trigger">
              <svg-icon icon-class="screen-more" />
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="8">
                  <svg-icon icon-class="screen8" class="dropdown-screen-icon" /> {{ $tp("八画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="16">
                  <svg-icon icon-class="screen16" class="dropdown-screen-icon" /> {{ $tp("十六画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="17">
                  <svg-icon icon-class="screen17" class="dropdown-screen-icon" /> {{ $tp("十七画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="21">
                  <svg-icon icon-class="screen21" class="dropdown-screen-icon" /> {{ $tp("二十一画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="23">
                  <svg-icon icon-class="screen23" class="dropdown-screen-icon" /> {{ $tp("二十三画面") }}
                </el-dropdown-item>
                <el-dropdown-item command="24">
                  <svg-icon icon-class="screen24" class="dropdown-screen-icon" /> {{ $tp("二十四画面") }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" @click="handleCustomScreen">{{ $tp("自定义") }}</el-button>
          <svg-icon class="flex-icon" icon-class="screen-full" @click="toggleWorkbenchFullscreen" />
        </div>
        <div class="workbench-players" ref="workbenchPlayersRef">
          <div class="players-grid" :style="gridContainerStyle">
            <div
                :id="'video' + index"
                v-for="(item, index) in currentSplitSlots"
                :key="`${splitShow}-${index}`"
                :style="getCellStyle(index)"
                :class="['player-cell', { active: activePlayerIndex === index, 'fallback-fullscreen': fallbackFullscreenIndex === index }]"
                @click="setActivePlayer(index)"
                @dblclick="togglePlayerFullscreen($event)">
              <div v-if="item.data" class="player-delete">
                <el-tooltip effect="dark" :content="$tp('删除')" placement="top">
                  <el-icon @click.stop="deleteVideo(index)"><Delete/></el-icon>
                </el-tooltip>
              </div>

              <template v-if="item.data">
                <div v-if="item.type === 'GB'" class="player-fill">
                  <Jessibuca v-show="vUrls[index]" :ref="'video' + index" :videoUrl="vUrls[index]" fluent autoplay live @dblclick.stop
                             :key="'jessibuca-'+index"/>
                </div>
                <div v-else class="player-fill">
                  <div v-if="item.data.playType === '2'" class="player-fill">
                    <Jessibuca :videoUrl="vUrls[index]" fluent autoplay live @dblclick.stop :key="'jessibuca-'+index"/>
                  </div>
                  <video v-else :id="'rtspVideo' + index" muted playsinline controls class="player-fill"></video>
                </div>
              </template>
              <div v-else class="player-empty">{{ $tp("无效信号") }}</div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <el-dialog v-model="customDialogVisible" :title="$tp('自定义视图')" width="26%" append-to-body destroy-on-close>
      <div class="custom-view-form">
        <div class="custom-view-field">
          <div class="custom-view-label">{{ $tp("行(输入值1-9)") }}</div>
          <el-input
              v-model="customRows"
              maxlength="1"
              inputmode="numeric"
              @input="onCustomNumInput('rows', $event)"
          />
        </div>
        <span class="custom-view-x">x</span>
        <div class="custom-view-field">
          <div class="custom-view-label">{{ $tp("列(输入值1-9)") }}</div>
          <el-input
              v-model="customCols"
              maxlength="1"
              inputmode="numeric"
              @input="onCustomNumInput('cols', $event)"
          />
        </div>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="confirmCustomScreen">{{ $tp("确定") }}</el-button>
        </div>
      </template>
    </el-dialog>

    <el-popover
        ref="popover"
        v-model:visible="contextMenuVisible"
        placement="bottom-start"
        width="80"
        :popper-style="{ left: `${menuPosition.x}px`, top: `${menuPosition.y}px`, position: 'absolute' }"
        trigger="manual">
      <div>
        <div @click="favorites" style="cursor: pointer;">{{ $tp("收藏设备") }}</div>
        <div @click="markNode" style="cursor: pointer;">{{ $tp("标记设备") }}</div>
      </div>
    </el-popover>

    <FavoriteDialog
        v-model="openFavorites"
        :selected-node="selectedNode"
        @success="handleFavoriteSuccess"
    />

    <MarkDialog
        v-model="openMarks"
        :selected-node="selectedNode"
        @success="handleMarkSuccess"
    />
  </div>
</template>

<script setup name="Work">
import {queryForTree} from "@/api/wvp/region";
import {queryListByCivilCode, sendDevicePush} from "@/api/wvp/channel.js";
import {queryForTree as groupQueryForTree} from "@/api/wvp/group.js";
import Jessibuca from "@/components/jessibuca/index-copy.vue";
import {start as playPush} from "@/api/wvp/push.js";
import {start as playProxy} from "@/api/wvp/proxy.js";
import {deviceList as onvifDeviceList, onvifPZTEnd, onvifPZTStart} from "../../api/onvif/device.js";
import {rtspDeviceList} from "../../api/rtsp/RtspDevice.js";
import {lsupDeviceList, ptzCtrlEnd, ptzCtrlFocus, ptzCtrlStart} from "../../api/isup/lsupDevice.js";
import InfiniteList from 'vue3-infinite-list';
import layouts from "./layouts.js";
import { getSplitLayout, getCustomEqualLayout, createSplitSlots } from "./splitScreenLayouts.js";
import {getConfigKey} from "../../api/system/config.js";
import {listWork, updateWork} from "../../api/system/work.js";
import {getFocusCamera, getIrIsCamera, getPtzCamera, queryListByParentId} from "../../api/wvp/channel.js";
import {listDahuaDevice, ptzControlUpEnd, ptzControlUpStart} from "../../api/dahua/device.js";
import {startPlay} from "../../api/wvp/push.js";
import {ElMessage} from "element-plus";
import {checkPermi} from "@/utils/permission";
import {ref} from "vue";
import {listFavoritesAll} from "@/api/wvp/favorites.js";
import {listMarkAll} from "@/api/wvp/mark.js";
import FavoriteDialog from './components/FavoriteDialog.vue'
import MarkDialog from './components/MarkDialog.vue'
import {listFavoritesChannel} from "@/api/wvp/favoritesChannel.js";
import {listWvpMarkChannel} from "@/api/wvp/wvpMarkChannel.js";

const contextMenuVisible = ref(false);
const openFavorites = ref(false);
const menuPosition = ref({x: 0, y: 0});
const selectedNode = ref(null);
const formFavorites = ref({
  favoritesId: undefined,
  channelId: undefined,
  gbName: undefined,
  gbParentId: undefined,
  gbDeviceId: undefined,
});
const rulesFavorites = ref({
  channelId: [{ required: true, get message() { return translatePhrase("请输入国标通道id") }, trigger: 'blur' }],
  gbName: [{ required: true, get message() { return translatePhrase("请输入国标通道名称") }, trigger: 'blur' }],
  gbParentid: [{ required: true, get message() { return translatePhrase("请输入国标设备id") }, trigger: 'blur' }],
  gbDeviceid: [{ required: true, get message() { return translatePhrase("请输入国标通道id") }, trigger: 'blur' }],
  favoritesId: [{ required: true, get message() { return translatePhrase("请选择收藏夹") }, trigger: 'change' }]
});
const rulesMarks = ref({
  channelId: [{ required: true, get message() { return translatePhrase("请输入国标通道id") }, trigger: 'blur' }],
  gbName: [{ required: true, get message() { return translatePhrase("请输入国标通道名称") }, trigger: 'blur' }],
  gbParentid: [{ required: true, get message() { return translatePhrase("请输入国标设备id") }, trigger: 'blur' }],
  gbDeviceid: [{ required: true, get message() { return translatePhrase("请输入国标通道id") }, trigger: 'blur' }],
  markId: [{ required: true, get message() { return translatePhrase("请选择标记") }, trigger: 'change' }]
});
const favoritesOptions = ref([]);
const closeDevice = ref(true);
const deviceName = ref("");
const listDevice = ref([])
const activeName = ref('GB')
const selectDeviceId = ref(null);
const showVideoDialog = ref(false);
const hasAudio = ref(false);
const vUrls = reactive({
  0: '',
  1: '',
  2: '',
  3: '',
  4: '',
  5: '',
  6: '',
  7: '',
  8: ''
});

const {proxy} = getCurrentInstance();

const queryParams = ref({
  pageNum: 1,
  pageSize: 200,
})

const video = ref(null);
const webRtcServer = ref();
const treeData = ref([]);
const rtspAddress = ref('');

const defaultProps = {
  children: 'children',
  label: 'name',
  isLeaf: 'leaf'
};
const splitLayouts = ref(JSON.parse(JSON.stringify(layouts)));

const handleFavoriteSuccess = () => {
  proxy.$modal.msgSuccess(translatePhrase("收藏成功"))
}

const handleMarkSuccess = () => {
  proxy.$modal.msgSuccess(translatePhrase("标记成功"))
}

function loadFavoritesOptions() {
  listFavoritesAll().then(res => {
    favoritesOptions.value = res.data;
  });
}

function favorites() {
  if (selectedNode.value) {
    formFavorites.value = {};
    loadFavoritesOptions();
    formFavorites.value.gbName = selectedNode.value.gbName;
    formFavorites.value.gbParentid = selectedNode.value.gbParentId;
    formFavorites.value.gbDeviceid = selectedNode.value.gbDeviceId;
    formFavorites.value.channelId = selectedNode.value.gbId;
    contextMenuVisible.value = false;
    openFavorites.value = true;
  } else {
    proxy.$modal.msgError(translatePhrase("请先选择国标通道"));
  }
}

const formMarks = ref({});
const markOptions = ref([]);
const openMarks = ref(false);

function loadMarksOptions() {
  listMarkAll().then(res => {
    markOptions.value = res.data;
  });
}

function markNode(event, node, treeNode) {
  if (selectedNode.value) {
    formMarks.value = {};
    loadMarksOptions();
    formMarks.value.gbName = selectedNode.value.gbName;
    formMarks.value.gbParentid = selectedNode.value.gbParentId;
    formMarks.value.gbDeviceid = selectedNode.value.gbDeviceId;
    formMarks.value.channelId = selectedNode.value.gbId;
    contextMenuVisible.value = false;
    openMarks.value = true;
  } else {
    proxy.$modal.msgError(translatePhrase("请先选择国标通道"));
  }
}


function handleRightClick(event, node, treeNode) {
  if (node.dataType) {
    event.preventDefault();
    selectedNode.value = node;
    menuPosition.value = {x: event.clientX, y: event.clientY};
    contextMenuVisible.value = true;
  }
}


async function onSwitch(e) {
  treeData.value = []
  if (activeValue.value) {
    await getTreeData();
  } else {
    await getGroupQueryForTree();
  }
}

const groupLoadNode = async (node, resolve) => {
  if (node.level === 0) {
    return resolve([{
      treeId: "",
      deviceId: "",
      get name() { return translatePhrase("根资源组") },
      isLeaf: false,
      type: 0
    }]);
  } else {
    if (node.data.leaf) {
      return resolve([])
    }
    let res = await groupQueryForTree({
      query: '',
      parent: node.data.id,
      hasChannel: ''
    });
    queryParams.value.groupDeviceId = node.data.deviceId;
    const response = await queryListByParentId(queryParams.value);
    const children = response.rows.map(item => ({
      ...item,
      leaf: true,
      name: item.gbName,
      iconClass: 'work-camera-1'
    }));
    let terr = [...proxy.handleTree(res.data, "id"), ...children]
    resolve(terr);
  }
}


const loadNode = async (node, resolve) => {
  if (node.level === 0) {
    return resolve([{
      treeId: "",
      deviceId: "",
      get name() { return translatePhrase("根资源组") },
      isLeaf: false,
      type: 0
    }]);
  } else if (node.data.deviceId.length <= 8) {
    if (node.data.leaf) {
      return resolve([])
    }
    let res = await queryForTree({
      query: '',
      parent: node.data.id,
      hasChannel: ''
    });
    queryParams.value.civilCode = node.data.deviceId;
    const response = await queryListByCivilCode(queryParams.value);
    const children = response.rows.map(item => ({
      ...item,
      leaf: true,
      name: item.gbName,
      iconClass: 'work-camera-1'
    }));
    let terr = [...proxy.handleTree(res.data, "id"), ...children]
    resolve(terr);
  } else {
    resolve([]);
  }
};

const highlightedNodeId = ref([]);

const handleNodeClick = async (data) => {
  if (!data.dataType) {
    return
  }
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (activeName.value === 'GB') {
    layoutData.type = 'GB'
    selectDeviceId.value = data.deviceId;
    layoutData.data = data
  }
  if (data.dataType === 1) {
    if (data.gbDeviceId && data.gbParentId) {
      const params = {
        deviceId: data.gbParentId,
        channelId: data.gbDeviceId
      }
      const res = await sendDevicePush(params);
      if (location.protocol === "https:") {
        vUrls[activePlayerIndex.value] = res.data.https_flv;
      } else {
        vUrls[activePlayerIndex.value] = res.data.flv;
      }

    } else {
      proxy.$modal.msgError(translatePhrase("通道或设备不存在"))
    }
  }

  if (data.dataType === 2) {
    const ans = await playPush({id: data.dataDeviceId});
    if (location.protocol === "https:") {
      vUrls[activePlayerIndex.value] = ans.https_flv;
    } else {
      vUrls[activePlayerIndex.value] = ans.flv;
    }
  }

  if (data.dataType === 3) {
    const ans = await playProxy({id: data.dataDeviceId});
    if (location.protocol === "https:") {
      vUrls[activePlayerIndex.value] = ans.https_flv;
    } else {
      vUrls[activePlayerIndex.value] = ans.flv;
    }
  }

  const highlightedNodeIds = ref(Array(9).fill(''));
  if (splitShow.value === 1) {
    if (highlightedNodeId.value.length === 0) {
      highlightedNodeId.value.push(data.gbId);
      const node = proxy.$refs["deviceTreeRef"].getNode(data.gbId);
      if (node && node.data) {
        node.data.iconClass = "work-camera-2";
      }
    } else {
      const node1 = proxy.$refs["deviceTreeRef"].getNode(highlightedNodeId.value[0]);
      if (node1 && node1.data) {
        node1.data.iconClass = "work-camera-1";
      }
      highlightedNodeId.value = [];
      highlightedNodeId.value.push(data.gbId);
      const node = proxy.$refs["deviceTreeRef"].getNode(data.gbId);
      if (node && node.data) {
        node.data.iconClass = "work-camera-2";
      }
    }
  } else if (splitShow.value === 4) {
    const activeIndex = activePlayerIndex.value;
    if (activeIndex === null || activeIndex >= 4) return;
    const previousId = highlightedNodeIds.value[activeIndex];
    if (previousId) {
      const previousNode = proxy.$refs["deviceTreeRef"].getNode(previousId);
      if (previousNode && previousNode.data) {
        previousNode.data.iconClass = "work-camera-1";
      }
    }
    highlightedNodeIds.value[activeIndex] = data.gbId;
    const currentNode = proxy.$refs["deviceTreeRef"].getNode(data.gbId);
    if (currentNode && currentNode.data) {
      currentNode.data.iconClass = "work-camera-2";
    }
  } else if (splitShow.value === 6) {
    const activeIndex = activePlayerIndex.value;
    if (activeIndex === null || activeIndex >= 6) return;
    const previousId = highlightedNodeIds.value[activeIndex];
    if (previousId) {
      const previousNode = proxy.$refs["deviceTreeRef"].getNode(previousId);
      if (previousNode && previousNode.data) {
        previousNode.data.iconClass = "work-camera-1";
      }
    }
    highlightedNodeIds.value[activeIndex] = data.gbId;
    const currentNode = proxy.$refs["deviceTreeRef"].getNode(data.gbId);
    if (currentNode && currentNode.data) {
      currentNode.data.iconClass = "work-camera-2";
    }
  } else if (splitShow.value === 9) {
    const activeIndex = activePlayerIndex.value;
    if (activeIndex === null || activeIndex >= 9) return;
    const previousId = highlightedNodeIds.value[activeIndex];
    if (previousId) {
      const previousNode = proxy.$refs["deviceTreeRef"].getNode(previousId);
      if (previousNode && previousNode.data) {
        previousNode.data.iconClass = "work-camera-1";
      }
    }
    highlightedNodeIds.value[activeIndex] = data.gbId;
    const currentNode = proxy.$refs["deviceTreeRef"].getNode(data.gbId);
    if (currentNode && currentNode.data) {
      currentNode.data.iconClass = "work-camera-2";
    }
  }
};

const splitShow = ref(4)
const borderWidth = ref(2)
const activePlayerIndex = ref(null);
const model = ref(4);
const activeValue = ref(true);
const customDialogVisible = ref(false)
const customRows = ref('1')
const customCols = ref('1')
const customLayout = ref(null)

const currentLayout = computed(() => {
  if (splitShow.value === 'custom' && customLayout.value) {
    return customLayout.value
  }
  return getSplitLayout(splitShow.value)
})
const currentSplitSlots = computed(() => {
  const slots = splitLayouts.value[splitShow.value]
  return Array.isArray(slots) ? slots : []
})
const gridContainerStyle = computed(() => ({
  display: 'grid',
  gridTemplateColumns: currentLayout.value.columns,
  gridTemplateRows: currentLayout.value.rows,
  gap: '2px',
  width: '100%',
  height: '100%',
  minHeight: '640px'
}))

function getCellStyle(index) {
  const cell = currentLayout.value.cells[index]
  if (!cell) return {}
  return {
    gridColumn: cell.column,
    gridRow: cell.row,
    display: "flex",
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#000000",
    boxSizing: "border-box",
    color: "#fff",
    fontSize: "14px",
    minHeight: 0,
    overflow: "hidden",
    border: `${borderWidth.value}px solid #2a2a2a`,
  };
}

const chooseId = ref(null);

function setActivePlayer(index) {
  activePlayerIndex.value = index;

  let layoutData = splitLayouts.value[splitShow.value][index];

  if (!layoutData || !layoutData.data) {
    return;
  }

  if (activeName.value === 'GB') {
    if (chooseId.value === null) {
      const targetId = layoutData.data.gbId;
      chooseId.value = targetId;
      const node = proxy.$refs["deviceTreeRef"].getNode(targetId);
      if (node && node.data) {
        node.data.iconClass = "work-camera-3";
      }
    } else {
      const node2 = proxy.$refs["deviceTreeRef"].getNode(chooseId.value);
      if (node2 && node2.data) {
        node2.data.iconClass = "work-camera-2";
      }
      const targetId = layoutData.data.gbId;
      chooseId.value = targetId;
      const node = proxy.$refs["deviceTreeRef"].getNode(targetId);
      if (node && node.data) {
        node.data.iconClass = "work-camera-3";
      }
    }


  }

}

async function getTreeData() {
  const res = await queryForTree();
  let data = [
    {
      treeId: "",
      deviceId: "",
      get name() { return translatePhrase("根资源组") },
      isLeaf: false,
      type: 0,
      children: []
    }
  ]
  data[0].children = proxy.handleTree(res.data, "id")
  treeData.value = data;
  getFavoriteTreeData();
  getMarkTreeData();
}

const treeFavoriteData = ref([]);
const queryParamsFavorite = ref({
  pageNum: 1,
  pageSize: 1000,
  favoritesId: null,
});
async function getFavoriteTreeData(){
  let data = [
    {
      treeId: 0,
      deviceId: "",
      get name() { return translatePhrase("收藏") },
      isLeaf: false,
      type: 0,
      children: []
    }
  ]
  treeFavoriteData.value = data;
}



const loadFavoriteNode = async (node, resolve) => {
  if (node.level === 0) {
    let data = [
      {
        treeId: 0,
        deviceId: "",
        get name() { return translatePhrase("收藏夹") },
        isLeaf: false,
        type: 0,
        children: []
      }
    ]
    resolve(data);
  } else {
    if (node.data.treeId === 0){
      const res = await listFavoritesAll();
      const dataWithNames = res.data.map(item => ({
        ...item,
        name: item.favoritesName,
        isLeaf: false,
        leaf: false,
      }));
      resolve(proxy.handleTree(dataWithNames, "id"));
    } else {
      queryParamsFavorite.value.favoritesId = node.data.id;
      const ans = await listFavoritesChannel(queryParamsFavorite.value);
      const o = ans.rows.map(item => ({
        ...item,
        name: item.gbName,
        isLeaf: true,
        leaf: true,
      }));
      resolve(proxy.handleTree(o, "channelId"));
    }
  }
}

const handleNodeFavoriteClick = async (data) => {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  const params = {
    deviceId: data.gbParentid,
    channelId: data.gbDeviceid
  }
  const res = await sendDevicePush(params);
  if (location.protocol === "https:") {
    vUrls[activePlayerIndex.value] = res.data.https_flv;
  } else {
    vUrls[activePlayerIndex.value] = res.data.flv;
    console.log(vUrls)
  }
}

const treeMarkData = ref([]);
const queryParamsMark = ref({
  pageNum: 1,
  pageSize: 10,
  markId: null,
});
function getMarkTreeData() {
  let data = [
    {
      treeId: 0,
      deviceId: "",
      get name() { return translatePhrase("标记") },
      isLeaf: false,
      type: 0,
      children: []
    }
  ]
  treeMarkData.value = data;
}

const loadMarkNode = async (node, resolve) => {
  if (node.level === 0) {
    let data = [
      {
        treeId: 0,
        deviceId: "",
        get name() { return translatePhrase("收藏夹") },
        isLeaf: false,
        type: 0,
        children: []
      }
    ]
    resolve(data);
  } else {
    if (node.data.treeId === 0){
      const res = await listMarkAll();
      const o = res.data.map(item => ({
        ...item,
        name: item.markName,
        isLeaf: false,
        leaf: false,
      }));
      resolve(proxy.handleTree(o, "id"));
    } else {
      queryParamsMark.value.markId = node.data.id;
      const ans = await listWvpMarkChannel(queryParamsMark.value);
      const o = ans.rows.map(item => ({
        ...item,
        name: item.gbName,
        isLeaf: true,
        leaf: true,
      }));
      resolve(proxy.handleTree(o, "channelId"));
    }
  }
}

const handleNodeMarkClick = async (data) => {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  const params = {
    deviceId: data.gbParentid,
    channelId: data.gbDeviceid
  }
  const res = await sendDevicePush(params);
  if (location.protocol === "https:") {
    vUrls[activePlayerIndex.value] = res.data.https_flv;
  } else {
    vUrls[activePlayerIndex.value] = res.data.flv;
    console.log(vUrls)
  }
}

async function getGroupQueryForTree() {
  const res = await groupQueryForTree();
  let data = [
    {
      treeId: "",
      deviceId: "",
      get name() { return translatePhrase("根资源组") },
      isLeaf: false,
      type: 0,
      children: []
    }
  ]
  data[0].children = proxy.handleTree(res.data, "id")
  treeData.value = data;
}

function spiltIndex(index) {
  const key = Number(index)
  if (!layouts[key]) {
    ElMessage.warning(translatePhrase("暂不支持该分屏"))
    return
  }
  customLayout.value = null
  splitLayouts.value = JSON.parse(JSON.stringify(layouts));
  splitShow.value = key;
  model.value = key;
  activePlayerIndex.value = null;
  selectDeviceId.value = null;
}

function handleScreenMore(command) {
  spiltIndex(command)
}

function handleCustomScreen() {
  customRows.value = '1'
  customCols.value = '1'
  customDialogVisible.value = true
}

function onCustomNumInput(which, val) {
  const raw = String(val ?? '').replace(/\D/g, '').slice(0, 1)
  if (which === 'rows') {
    customRows.value = raw
  } else {
    customCols.value = raw
  }
}

function confirmCustomScreen() {
  const rows = Number(customRows.value)
  const cols = Number(customCols.value)
  if (!Number.isInteger(rows) || !Number.isInteger(cols) || rows < 1 || rows > 9 || cols < 1 || cols > 9) {
    ElMessage.warning(translatePhrase("行和列请输入 1-9 的整数"))
    return
  }
  const layout = getCustomEqualLayout(rows, cols)
  customLayout.value = layout
  splitLayouts.value = {
    ...JSON.parse(JSON.stringify(layouts)),
    custom: createSplitSlots(layout, () => ({ type: '', data: null }))
  }
  splitShow.value = 'custom'
  model.value = 'custom'
  activePlayerIndex.value = null
  selectDeviceId.value = null
  customDialogVisible.value = false
}

const workbenchPlayersRef = ref(null)
const fallbackFullscreenIndex = ref(null)

function toggleWorkbenchFullscreen() {
  const el = workbenchPlayersRef.value
  if (!el) return
  if (!document.fullscreenElement) {
    el.requestFullscreen?.()
  } else {
    document.exitFullscreen?.()
  }
}

async function togglePlayerFullscreen(event) {
  const playerCell = event.currentTarget
  const playerIndex = Number(playerCell.id.replace('video', ''))

  if (fallbackFullscreenIndex.value === playerIndex) {
    fallbackFullscreenIndex.value = null
    return
  }

  if (document.fullscreenElement === playerCell) {
    await document.exitFullscreen?.()
    return
  }

  if (typeof playerCell.requestFullscreen === 'function' && document.fullscreenEnabled) {
    try {
      await playerCell.requestFullscreen()
      return
    } catch (error) {
      // 部分内嵌浏览器禁止原生全屏，改为页面内全屏，仍保持同样的观看体验。
    }
  }

  fallbackFullscreenIndex.value = playerIndex
}

function handleFullscreenEscape(event) {
  if (event.key === 'Escape') fallbackFullscreenIndex.value = null
}

const handleClick = (tab, event) => {
  nextTick(async () => {
    deviceName.value = '';
    await getList();
  })
}

function getListWork() {
  listWork().then(async (res) => {
    // 默认四屏并激活图标；忽略历史保存的分屏模式，避免覆盖
    customLayout.value = null
    splitLayouts.value = JSON.parse(JSON.stringify(layouts))
    splitShow.value = 4
    model.value = 4

    if (res.data && res.data.layoutList) {
      let saved
      try {
        saved = JSON.parse(res.data.layoutList)
      } catch (e) {
        return
      }
      const savedIndex = Number(res.data.index)
      const sourceSlots = (saved && (saved[savedIndex] || saved[4] || saved['4'])) || []
      const targetSlots = splitLayouts.value[4] || []
      sourceSlots.forEach((item, index) => {
        if (index >= targetSlots.length || !item || item.type === '') return
        targetSlots[index] = {
          ...targetSlots[index],
          type: item.type,
          data: item.data
        }
        if (item.type === 'ONVIF') {
          playVideo(item.data.playType, item.data.url, item.data.easyNTSUrl, item.data.streamId, index)
        } else if (item.type === 'RTSP') {
          playVideo(item.data.playType, item.data.url, item.data.easyNTSUrl, item.data.streamId, index)
        } else if (item.type === 'ISUP') {
          playVideo(item.data.playType, item.data.url, item.data.easyNTSUrl, item.data.streamId, index)
        } else if (item.type === 'GB') {
          nextTick(() => {
            playGB(item.data, index)
          })
        } else if (item.type === 'DAHUA') {
          playVideo(item.data.playType, item.data.url, item.data.easyNTSUrl, item.data.streamId, index)
        }
      })
    }
  })
}

function playVideo(playType, url, easyNTSUrl, streamId, index) {
  nextTick(async () => {
    if (playType === '1') {
      webRtcServer.value = new WebRtcStreamer('rtspVideo' + index, rtspAddress.value);
      webRtcServer.value.connect(url)
    } else if (playType === '3') {
      webRtcServer.value = new WebRtcStreamer('rtspVideo' + index, rtspAddress.value);
      webRtcServer.value.connect(easyNTSUrl)
    } else if (playType === '2') {
      const ans = await startPlay(streamId);
      if (location.protocol === "https:") {
        vUrls[index] = ans.data.https_flv;
      } else {
        vUrls[index] = ans.data.flv;
      }
    }
  })
}

async function playGB(data, index) {
  if (data.dataType === 1) {
    if (data.gbDeviceId && data.gbParentId) {
      const params = {
        deviceId: data.gbParentId,
        channelId: data.gbDeviceId
      }
      const res = await sendDevicePush(params);
      if (location.protocol === "https:") {
        vUrls[index] = res.data.https_flv;
      } else {
        vUrls[index] = res.data.flv;
      }
    } else {
      proxy.$modal.msgError(translatePhrase("通道或设备不存在"))
    }
  }

  if (data.dataType === 2) {
    const ans = await playPush({id: data.dataDeviceId});
    if (location.protocol === "https:") {
      vUrls[index] = ans.https_flv;
    } else {
      vUrls[index] = ans.flv;
    }
  }

  if (data.dataType === 3) {
    const ans = await playProxy({id: data.dataDeviceId});
    if (location.protocol === "https:") {
      vUrls[index] = ans.https_flv;
    } else {
      vUrls[index] = ans.flv;
    }
  }


}

async function getList() {
  listDevice.value = []
  if (activeName.value === 'GB') {
    await getTreeData();
  } else if (activeName.value === 'ONVIF') {
    onvifDeviceList({
      name: deviceName.value
    }).then(res => {
      listDevice.value = res.data
    })
  } else if (activeName.value === 'RTSP') {
    rtspDeviceList({
      name: deviceName.value
    }).then(res => {
      listDevice.value = res.data
    })
  } else if (activeName.value === 'ISUP') {
    lsupDeviceList({
      name: deviceName.value
    }).then(res => {
      listDevice.value = res.data
    })
  } else if (activeName.value === 'DAHUA') {
    listDahuaDevice({
      name: deviceName.value
    }).then(res => {
      listDevice.value = res.data
    })
  }
}

async function deviceClick(data) {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }

  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]

  if (activeName.value === 'ONVIF') {
    layoutData.type = 'ONVIF'
  } else if (activeName.value === 'RTSP') {
    layoutData.type = 'RTSP'
  } else if (activeName.value === 'ISUP') {
    layoutData.type = 'ISUP'
  } else if (activeName.value === 'DAHUA') {
    layoutData.type = 'DAHUA'
    selectDeviceId.value = data.id;

    let newUrl = data.url.replace(/(channel=)(\d+)/, function (_, p1, p2) {
      return p1 + (parseInt(p2) + 1);
    });

    layoutData.data = data;
    playVideo(data.playType, newUrl, data.easyNTSUrl, data.streamId, activePlayerIndex.value)
    return
  }

  selectDeviceId.value = data.id;
  layoutData.data = data;

  playVideo(data.playType, data.url, data.easyNTSUrl, data.streamId, activePlayerIndex.value)
}

onUnmounted(() => {
  if (webRtcServer.value) {
    webRtcServer.value.disconnect()
    webRtcServer.value = null
  }
})

function getConfigKeyFun() {
  getConfigKey("sys_rtsp_address").then((res) => {
    rtspAddress.value = res.msg
  })
}

function handleSave() {
  updateWork({
    layoutList: JSON.stringify(splitLayouts.value),
    index: splitShow.value
  }).then(() => {
    proxy.$modal.msgSuccess(translatePhrase("保存成功"));
  })
}

function handleCleanUp() {
  proxy.$modal.confirm(translatePhrase("是否清除显示的分屏？")).then(function () {
    splitLayouts.value = JSON.parse(JSON.stringify(layouts));
    return
  }).then(() => {
    handleSave()
  }).catch(() => {
  });

}

function deleteVideo(index) {
  proxy.$modal.confirm(translatePhrase("是否删除该分屏")).then(function () {
    let layoutData = splitLayouts.value[splitShow.value][index]
    layoutData.data = null
    layoutData.type = ''
  }).then(() => {
    proxy.$modal.msgSuccess(translatePhrase("删除成功"));
  }).catch(() => {
  });
}


/** 根据名称筛选部门树 */
watch(deviceName, val => {
  proxy.$refs["deviceTreeRef"].filter(val);
});

/** 通过条件过滤节点  */
const filterNode = (value, data) => {
  if (!value) return true;
  return data.name.indexOf(value) !== -1;
};

const deviceChange = () => {
  getList()
}


// 云台
const controSpeed = ref(30);
const dahuaControSpeed = ref(5);
const haikangControSpeed = ref(5);
const haikangControSpeedFocus = ref(0);
const onvifControSpeed = ref(0.1);

const ptzCamera = async (command) => {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  const url = {
    deviceId: layoutData.data.gbParentId,
    channelId: layoutData.data.gbDeviceId,
  }
  const params = {
    command: command,
    horizonSpeed: parseInt(controSpeed.value * 255 / 100),
    verticalSpeed: parseInt(controSpeed.value * 255 / 100),
    zoomSpeed: parseInt(controSpeed.value * 16 / 100),
  }
  await getPtzCamera(url, params);
}

const focusCamera = async (command) => {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  const url = {
    deviceId: layoutData.data.gbParentId,
    channelId: layoutData.data.gbDeviceId,
  }
  const params = {
    command: command,
    speed: parseInt(controSpeed.value * 255 / 100),
  }
  await getFocusCamera(url, params);
  ElMessage.success(translatePhrase("操作成功！"));
}

const irisCamera = async (command) => {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  const url = {
    deviceId: layoutData.data.gbParentId,
    channelId: layoutData.data.gbDeviceId,
  }
  const params = {
    command: command,
    speed: parseInt(controSpeed.value * 255 / 100),
  }
  await getIrIsCamera(url, params);
  ElMessage.success(translatePhrase("操作成功！"));
}

/**
 * 大华设备云台控制（开始）
 *
 * @param direction
 */
function ptzControlUpStartFun(direction) {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  ptzControlUpStart(layoutData.data.id, direction, dahuaControSpeed.value)
}

/**
 * 大华设备云台控制（开始）
 *
 * @param direction
 */
function ptzControlUpEndFun(direction) {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  setTimeout(() => {
    ptzControlUpEnd(layoutData.data.id, direction)
  }, 200)
}


/**
 * 海康云台控制（开始）
 */
function ptzCtrlStartFun(direction) {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  ptzCtrlStart(layoutData.data.id, direction, controSpeed.value)
}

/**
 * 海康云台控制（结束）
 */
function ptzCtrlEndFun() {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  ptzCtrlEnd(layoutData.data.id)
}

/**
 * 海康聚焦
 */
function haikangFocusCamera() {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  ptzCtrlFocus(layoutData.data.id, haikangControSpeedFocus.value)
}

/**
 * 云台开始
 *
 * @param direction
 */
function onvifPtzCtrlStartFun(direction) {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  onvifPZTStart({
    direction: direction,
    id: layoutData.data.id,
    controSpeed: onvifControSpeed.value,
  });
}

/**
 * 云台结束
 */
function onvifPtzCtrlEndFun() {
  if (activePlayerIndex.value == null) {
    proxy.$modal.msgError(translatePhrase("请先选择一个播放窗口"));
    return
  }
  let layoutData = splitLayouts.value[splitShow.value][activePlayerIndex.value]
  if (!layoutData.data) {
    proxy.$modal.msgError(translatePhrase("该窗口没有播放视频"));
    return
  }
  setTimeout(() => {
    onvifPZTEnd({
      id: layoutData.data.id,
    });
  }, 200)
}

onMounted(async () => {
  document.addEventListener('keydown', handleFullscreenEscape)
  await getConfigKeyFun()
  await getList()
  await getListWork()
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleFullscreenEscape)
})

</script>

<style scoped>

.app-container.workbench-page {
  background: #fff;
  border-radius: 10px;
  min-height: calc(100vh - 84px);
  box-sizing: border-box;
  padding: 16px 20px;
}

:deep(.work-tabs) {
  --el-tabs-header-height: 32px;

  .el-tabs__header {
    margin: 0 0 12px;
    border-bottom: 1px solid #f0f0f0;
  }

  .el-tabs__nav-wrap::after {
    display: none;
  }

  .el-tabs__item {
    width: 96px;
    height: 32px;
    padding: 0;
    line-height: 32px;
    text-align: center;
    color: #333;
    font-size: 14px;
    font-weight: 400;
    box-sizing: border-box;
  }

  .el-tabs__item.is-active {
    color: var(--el-color-primary);
  }

  .el-tabs__item:hover {
    color: var(--el-color-primary);
  }

  .el-tabs__active-bar {
    height: 2px;
    background-color: var(--el-color-primary);
  }

  .el-tabs__content {
    display: none;
  }
}

.workbench-layout {
  display: flex;
  align-items: stretch;
  gap: 20px;
  min-height: calc(100vh - 160px);
  background: #fff;
}

.workbench-aside {
  width: 300px;
  flex-shrink: 0;
  padding: 0 20px 0 0;
  margin: 0;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
  border-radius: 0;
  line-height: normal;
  font-size: inherit;
  color: inherit;
}

.aside-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.aside-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  padding-left: 8px;
  border-left: 3px solid var(--el-color-primary);
  line-height: 1;
}

.aside-actions {
  display: flex;
  gap: 4px;
}

.aside-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.workbench-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.workbench-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.workbench-players {
  flex: 1;
  min-height: 640px;
  background: #f5f5f5;
  border-radius: 4px;
  overflow: hidden;
}

.players-grid {
  position: relative;
  width: 100%;
  height: 100%;
  min-height: 640px;
  background: #000;
}

.player-fill {
  width: 100%;
  height: 100%;
}

.player-empty {
  color: #fff;
  font-size: 14px;
  user-select: none;
}

.player-delete {
  position: absolute;
  z-index: 999;
  top: 5px;
  right: 10px;
  color: #F56C6C;
}

.player-cell {
  position: relative;
  transition: border-color 0.3s ease;
  border: 2px solid transparent;
  overflow: hidden;
}

.player-cell:hover {
  cursor: pointer;
}

.player-cell.active {
  border-color: var(--el-color-primary) !important;
}

.player-cell:fullscreen {
  border: 0 !important;
  background: #000;
}

.player-cell.fallback-fullscreen {
  position: fixed !important;
  inset: 0 !important;
  z-index: 3000;
  width: 100vw !important;
  height: 100vh !important;
  border: 0 !important;
  background: #000;
}

.more-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
}

.dropdown-screen-icon {
  margin-right: 8px;
  font-size: 16px;
  vertical-align: middle;
}

.custom-view-form {
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 16px;
  padding: 8px 0 16px;
}

.custom-view-field {
  width: 120px;
}

.custom-view-label {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
  white-space: nowrap;
}

.custom-view-x {
  padding-bottom: 8px;
  font-size: 14px;
  color: #333;
}

.el-tree {
  min-width: 100%;
  display: inline-block;
}

.tree {
  overflow: auto;
  max-height: 440px;
}

.top {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.flex {
  width: 100%;
  display: flex;
  align-items: center;
}

.flex-icon {
  cursor: pointer;
  width: 24px;
  height: 24px;
  font-size: 24px;
  transition: color 0.3s ease, transform 0.3s ease;
  color: #666;
}

.flex-icon.active {
  color: var(--el-color-primary);
  transform: scale(1.1);
}

.workbench-toolbar .flex-icon,
.workbench-toolbar .svg-icon,
.workbench-toolbar .more-trigger .svg-icon {
  width: 24px;
  height: 24px;
  font-size: 24px;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  padding-right: 8px;
}

.example-showcase .el-dropdown-link {
  cursor: pointer;
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
}

.player {
  width: 100%;
  height: 450px;
}

::v-deep(.el-icon) {
  height: auto !important;
}

.control-wrapper {
  position: relative;
  width: 100px; /* 6.25rem * 16 = 100px */
  height: 100px; /* 6.25rem * 16 = 100px */
  max-width: 100px; /* 6.25rem * 16 = 100px */
  max-height: 100px; /* 6.25rem * 16 = 100px */
  border-radius: 100%;
  margin-top: 24px; /* 1.5rem * 16 = 24px */
  margin-left: 8px; /* 0.5rem * 16 = 8px */
  float: left;
}

.control-panel {
  position: relative;
  top: 0;
  left: 80px; /* 5rem * 16 = 80px */
  height: 176px; /* 11rem * 16 = 176px */
  max-height: 176px; /* 11rem * 16 = 176px */
}

.control-btn {
  display: flex;
  justify-content: center;
  position: absolute;
  width: 44%;
  height: 44%;
  border-radius: 5px;
  border: 1px solid #78aee4;
  box-sizing: border-box;
  transition: all 0.3s linear;
}


.control-btn:hover {
  cursor: pointer;
}

.control-btn .icon {
  width: 100%;
  font-size: 20px;
  color: #78aee4;
  display: flex;
  justify-content: center;
  align-items: center;
}

.control-btn .icon:hover {
  cursor: pointer;
}

.control-zoom-btn:hover {
  cursor: pointer;
}

.control-round {
  position: absolute;
  top: 21%;
  left: 21%;
  width: 58%;
  height: 58%;
  background: #fff;
  border-radius: 100%;
}

.control-round-inner {
  position: absolute;
  left: 13%;
  top: 13%;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 70%;
  height: 70%;
  font-size: 40px;
  color: #78aee4;
  border: 1px solid #78aee4;
  border-radius: 100%;
  transition: all 0.3s linear;
}

.control-inner-btn {
  position: absolute;
  width: 60%;
  height: 60%;
  background: #fafafa;
}

.control-top {
  top: -12px; /* -8% of 100px ≈ -12px */
  left: 27%;
  transform: rotate(-45deg);
  border-radius: 5px 100% 5px 0;
}

.control-top .icon {
  transform: rotate(45deg);
  border-radius: 5px 100% 5px 0;
}

.control-top .control-inner {
  left: -1px;
  bottom: 0;
  border-top: 1px solid #78aee4;
  border-right: 1px solid #78aee4;
  border-radius: 0 100% 0 0;
}

.control-top .fa {
  transform: rotate(45deg) translateY(-7px);
}

.control-left {
  top: 27%;
  left: -12px; /* -8% of 100px ≈ -12px */
  transform: rotate(45deg);
  border-radius: 5px 0 5px 100%;
}

.control-left .icon {
  transform: rotate(-45deg);
}

.control-left .control-inner {
  right: -1px;
  top: -1px;
  border-bottom: 1px solid #78aee4;
  border-left: 1px solid #78aee4;
  border-radius: 0 0 0 100%;
}

.control-left .fa {
  transform: rotate(-45deg) translateX(-7px);
}

.control-right {
  top: 27%;
  right: -12px; /* -8% of 100px ≈ -12px */
  transform: rotate(45deg);
  border-radius: 5px 100% 5px 0;
}

.control-right .icon {
  transform: rotate(-45deg);
}

.control-right .control-inner {
  left: -1px;
  bottom: -1px;
  border-top: 1px solid #78aee4;
  border-right: 1px solid #78aee4;
  border-radius: 0 100% 0 0;
}

.control-right .fa {
  transform: rotate(-45deg) translateX(7px);
}

.control-bottom {
  left: 27%;
  bottom: -12px; /* -8% of 100px ≈ -12px */
  transform: rotate(45deg);
  border-radius: 0 5px 100% 5px;
}

.control-bottom .icon {
  transform: rotate(-45deg);
}

.control-bottom .control-inner {
  top: -1px;
  left: -1px;
  border-bottom: 1px solid #78aee4;
  border-right: 1px solid #78aee4;
  border-radius: 0 0 100% 0;
}

.control-bottom .fa {
  transform: rotate(-45deg) translateY(7px);
}

.trank {
  width: 80%;
  height: 180px;
  text-align: left;
  padding: 0 10%;
  overflow: auto;
}

.trankInfo {
  width: 80%;
  padding: 0 10%;
}

.el-dialog__body {
  padding: 10px 20px;
}

.ptz-btn-box {
  display: grid;
  grid-template-columns: 1fr 1fr;
  padding: 0 32px; /* 2rem * 16 = 32px */
  height: 48px; /* 3rem * 16 = 48px */
  line-height: 64px; /* 4rem * 16 = 64px */
}
</style>

