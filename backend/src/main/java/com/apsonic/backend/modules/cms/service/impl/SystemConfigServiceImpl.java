package com.apsonic.backend.modules.cms.service.impl;

import com.apsonic.backend.modules.cms.domain.SystemConfig;
import com.apsonic.backend.modules.cms.dto.CmsConfigRequest;
import com.apsonic.backend.modules.cms.dto.CmsConfigResponse;
import com.apsonic.backend.modules.cms.repository.SystemConfigRepository;
import com.apsonic.backend.modules.cms.service.SystemConfigService;
import com.apsonic.backend.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository systemConfigRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "cmsConfigs", key = "#configKey")
    public CmsConfigResponse getConfig(String configKey) {
        SystemConfig config = systemConfigRepository.findByConfigKey(configKey)
                .orElseThrow(() -> new ResourceNotFoundException("SystemConfig", "configKey", configKey));
        
        return CmsConfigResponse.builder()
                .configKey(config.getConfigKey())
                .configValue(config.getConfigValue())
                .description(config.getDescription())
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = "cmsConfigs", key = "#configKey")
    public CmsConfigResponse saveConfig(String configKey, CmsConfigRequest request) {
        SystemConfig config = systemConfigRepository.findByConfigKey(configKey)
                .orElseGet(() -> {
                    SystemConfig newConfig = new SystemConfig();
                    newConfig.setConfigKey(configKey);
                    return newConfig;
                });

        config.setConfigValue(request.getConfigValue());
        if (request.getDescription() != null) {
            config.setDescription(request.getDescription());
        }

        SystemConfig savedConfig = systemConfigRepository.save(config);

        return CmsConfigResponse.builder()
                .configKey(savedConfig.getConfigKey())
                .configValue(savedConfig.getConfigValue())
                .description(savedConfig.getDescription())
                .build();
    }
}
