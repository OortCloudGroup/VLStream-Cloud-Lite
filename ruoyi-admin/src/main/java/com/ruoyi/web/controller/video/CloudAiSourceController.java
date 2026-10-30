package com.ruoyi.web.controller.video;

import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.web.service.CloudAiSourceService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/video/cloud-ai")
public class CloudAiSourceController {
    private final CloudAiSourceService sources;
    public CloudAiSourceController(CloudAiSourceService sources) { this.sources = sources; }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/sources/{protocol}/{rowId}")
    public AjaxResult choices(@PathVariable String protocol, @PathVariable Long rowId) {
        return AjaxResult.success(sources.choices(protocol, rowId));
    }
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/devices/{protocol}")
    public AjaxResult devices(@PathVariable String protocol,@RequestParam(defaultValue="1") int pageNum,
            @RequestParam(defaultValue="10") int pageSize,@RequestParam(required=false) String name) {
        return AjaxResult.success(sources.devices(protocol,pageNum,pageSize,name));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/grants")
    public AjaxResult grant(@RequestBody CloudAiSourceService.Selection request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return AjaxResult.success(sources.grant(request));
    }

    // Authentication here is the unguessable, revocable source grant; never an anonymous device lookup.
    @Anonymous
    @PostMapping("/grants/resolve")
    public AjaxResult resolve(@RequestBody GrantToken request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); return AjaxResult.success(sources.resolve(request.token,request.runId));
    }

    @Anonymous
    @PostMapping("/grants/revoke")
    public AjaxResult revoke(@RequestBody GrantToken request) { sources.revoke(request.token); return AjaxResult.success(); }
    @Anonymous
    @PostMapping("/grants/release")
    public AjaxResult release(@RequestBody GrantToken request) { sources.release(request.token,request.runId);return AjaxResult.success(java.util.Collections.singletonMap("released",true)); }

    public static class GrantToken { public String token; public String runId; }
}
