package org.avni.server.web.external;

import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.dao.UserGroupRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.UserGroup;
import org.avni.server.framework.security.AuthenticationFilter;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.rules.RulesContractWrapper.MyUserGroupContract;
import org.avni.server.web.request.rules.RulesContractWrapper.UserContract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RuleServiceClient {
    private final RestTemplate restTemplate;
    private final UserGroupRepository userGroupRepository;
    @Value("${node.server.url}")
    private String NODE_SERVER_HOST;
    private final Logger logger = LoggerFactory.getLogger(RuleServiceClient.class);

    @Autowired
    public RuleServiceClient(RestTemplate restTemplate, UserGroupRepository userGroupRepository) {
        this.restTemplate = restTemplate;
        this.userGroupRepository = userGroupRepository;
    }

    public Object post(String api, Object jsonObj, Class responseType) throws HttpClientErrorException {
        String uri = NODE_SERVER_HOST.concat(api);
        Object body = wrapBodyWithCurrentUser(jsonObj);
        HttpEntity<Object> entityCredentials = new HttpEntity<>(body, constructHeaders());
        try {
            return restTemplate.postForObject(uri, entityCredentials, responseType);
        } catch (HttpClientErrorException e) {
            logger.info("rule " + api + " not found");
            throw new HttpClientErrorException(HttpStatus.NOT_FOUND, "rule " + api + " not found");
        }
    }

    private Object wrapBodyWithCurrentUser(Object jsonObj) {
        if (jsonObj == null) return jsonObj;
        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        Organisation organisation = userContext.getOrganisation();
        List<UserGroup> userGroups = userGroupRepository.findByUser_IdAndIsVoidedFalse(user.getId());

        Map<String, Object> bodyMap = ObjectMapperSingleton.getObjectMapper().convertValue(jsonObj, new TypeReference<Map<String, Object>>() {});
        bodyMap.put("currentUser", UserContract.fromUser(user, organisation, userGroups));
        bodyMap.put("myUserGroups", userGroups.stream()
                .map(MyUserGroupContract::fromEntity)
                .collect(Collectors.toList()));
        return bodyMap;
    }

    private HttpHeaders constructHeaders() {
        HttpHeaders httpHeaders = new HttpHeaders();
        UserContext userContext = UserContextHolder.getUserContext();
        String userName = userContext.getUserName();
        String organisationUUID = userContext.getOrganisation().getUuid();
        String authToken = userContext.getAuthToken();

        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        if(userName != null)
            httpHeaders.add(AuthenticationFilter.USER_NAME_HEADER, userName);
        if(organisationUUID != null)
            httpHeaders.add(AuthenticationFilter.ORGANISATION_UUID, organisationUUID);
        if(authToken != null)
            httpHeaders.add(AuthenticationFilter.AUTH_TOKEN_HEADER, authToken);
        return httpHeaders;
    }
}
