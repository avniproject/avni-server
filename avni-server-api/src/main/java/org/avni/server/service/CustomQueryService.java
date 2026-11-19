package org.avni.server.service;

import org.avni.server.domain.CustomQuery;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.web.request.CustomQueryContract;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomQueryService {

    /**
     * A saved query is raw SQL that /executeQuery later runs, so a bundle must never be able to add or change one.
     * This holds for every uploader: an organisation's admin (including a self-signup trial admin) would otherwise
     * plant SQL, and Avni staff cannot launch a bundle upload anyway. Saved queries are added by the Avni team directly.
     */
    public void processCustomQueries(List<CustomQueryContract> customQueryContracts) {
        if (customQueryContracts == null || customQueryContracts.isEmpty()) return;
        throw AvniAccessException.createForSavedQueriesInBundle();
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
