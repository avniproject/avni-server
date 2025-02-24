package org.avni.server.service;

import org.avni.server.dao.CustomQueryRepository;
import org.avni.server.domain.CustomQuery;
import org.avni.server.web.request.CustomQueryContract;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomQueryService {
    private final CustomQueryRepository customQueryRepository;

    public CustomQueryService(CustomQueryRepository customQueryRepository) {
        this.customQueryRepository = customQueryRepository;
    }

    private void upsertCustomQuery(CustomQuery customQuery) {
        CustomQuery savedQuery = customQueryRepository.findByUuid(customQuery.getUuid());
        if (savedQuery == null) {
            customQueryRepository.save(customQuery);
        }else{
            savedQuery.setName(customQuery.getName());
            savedQuery.setQuery(customQuery.getQuery());
            savedQuery.setVoided(customQuery.isVoided());
            savedQuery.setVersion(customQuery.getVersion());
            customQueryRepository.save(savedQuery);
        }
    }

    public void processCustomQueries(List<CustomQueryContract> customQueryContracts) {
        customQueryContracts.stream()
                .map(this::dtoToEntity)
                .forEach(this::upsertCustomQuery);
    }

    public CustomQuery dtoToEntity(CustomQueryContract customQueryContract){
        CustomQuery customQuery = new CustomQuery();
        customQuery.setUuid(customQueryContract.getUuid());
        customQuery.setName(customQueryContract.getName());
        customQuery.setQuery(customQueryContract.getQuery());
        customQuery.setVoided(customQueryContract.isVoided());
        customQuery.setVersion(customQueryContract.getVersion());
        return customQuery;
    }

    public CustomQueryContract EntityToDto(CustomQuery customQuery){
        CustomQueryContract customQueryContract = new CustomQueryContract();
        customQueryContract.setUuid(customQuery.getUuid());
        customQueryContract.setName(customQuery.getName());
        customQueryContract.setQuery(customQuery.getQuery());
        customQueryContract.setVoided(customQuery.isVoided());
        customQueryContract.setVersion(customQuery.getVersion());
        return customQueryContract;
    }
}
