package com.ruoyi.onvif.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.onvif.domain.OnvifDevice;
import com.ruoyi.onvif.domain.bo.AbsoluteMoveBo;
import com.ruoyi.onvif.domain.bo.PresetsBo;
import com.ruoyi.onvif.utils.OnvifPtzCapabilities;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** ID-based adapter; the existing scoped reader and per-action controller permissions stay authoritative. */
@Service
public class OnvifDevicePresetService {
    private final OnvifDeviceScopedReader reader;
    private final IOnvifService api;
    private final Function<OnvifDevice, Map<String, Object>> capabilities;

    @Autowired
    public OnvifDevicePresetService(OnvifDeviceScopedReader reader, IOnvifService api) {
        this(reader, api, device -> OnvifPtzCapabilities.getInstance().inspect(device));
    }

    OnvifDevicePresetService(OnvifDeviceScopedReader reader, IOnvifService api,
            Function<OnvifDevice, Map<String, Object>> capabilities) {
        this.reader = reader;
        this.api = api;
        this.capabilities = capabilities;
    }

    private PresetsBo context(Long id) {
        if (id == null || id <= 0) throw new ServiceException("设备ID无效", 400);
        OnvifDevice query = new OnvifDevice();
        query.setId(id);
        OnvifDevice device = reader.forOperation(query, false).stream()
                .filter(row -> id.equals(row.getId())).findFirst()
                .orElseThrow(() -> new ServiceException("设备不存在或无权访问", 403));
        Map<String, Object> state = capabilities.apply(device);
        if (state == null || !"supported".equals(state.get("status")) || !Boolean.TRUE.equals(state.get("presets")))
            throw new ServiceException("预置点不可用，请重新检查云台能力", 400);
        Object profile = state.get("profileToken");
        if (!(profile instanceof String) || ((String) profile).trim().isEmpty())
            throw new ServiceException("设备未返回有效Profile", 400);
        PresetsBo result = new PresetsBo();
        result.setIp(device.getIp());
        result.setUsername(xml(device.getUserName()));
        result.setPassword(device.getPassword());
        result.setProfileToken(xml((String) profile));
        return result;
    }

    public List<Map<String, String>> list(Long id) {
        PresetsBo context = context(id);
        AbsoluteMoveBo query = new AbsoluteMoveBo();
        query.setIp(context.getIp());
        query.setUsername(context.getUsername());
        query.setPassword(context.getPassword());
        query.setProfileToken(context.getProfileToken());
        List<Map<String, String>> rows;
        try { rows = api.getPresetList(query); }
        catch (RuntimeException failure) { throw new ServiceException("获取预置点失败，请重试", 502); }
        List<Map<String, String>> result = new ArrayList<>();
        if (rows != null) for (Map<String, String> row : rows) {
            if (row == null || row.get("token") == null || row.get("token").trim().isEmpty()) continue;
            Map<String, String> safe = new LinkedHashMap<>();
            safe.put("token", row.get("token"));
            safe.put("name", row.get("name") == null ? "" : row.get("name"));
            result.add(safe);
        }
        return result;
    }

    public void create(Long id, String name) {
        PresetsBo request = context(id);
        request.setPresetToken(xml(text(name, 128, true)));
        try { api.addPreset(request); }
        catch (RuntimeException failure) { throw new ServiceException("添加预置点结果未确认，请刷新列表", 502); }
    }
    public void goTo(Long id, String token) {
        PresetsBo request = context(id);
        request.setPresetToken(xml(text(token, 256, false)));
        try { api.gotoPreset(request); }
        catch (RuntimeException failure) { throw new ServiceException("预置点调用结果未确认，请检查设备", 502); }
    }
    public void remove(Long id, String token) {
        PresetsBo request = context(id);
        request.setPresetToken(xml(text(token, 256, false)));
        try { api.removePreset(request); }
        catch (RuntimeException failure) { throw new ServiceException("删除预置点结果未确认，请刷新列表", 502); }
    }

    private static String text(String value, int max, boolean trim) {
        if (value == null || value.trim().isEmpty() || value.length() > max)
            throw new ServiceException("预置点名称或标识无效", 400);
        for (char c : value.toCharArray()) if (c < 32 || c == 127)
            throw new ServiceException("预置点名称或标识无效", 400);
        return trim ? value.trim() : value;
    }
    // Legacy SOAP builders concatenate text; escape exactly once without changing the digest password.
    private static String xml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
