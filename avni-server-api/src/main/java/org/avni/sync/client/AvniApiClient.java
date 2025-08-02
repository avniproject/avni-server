package org.avni.sync.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.sync.model.EntitySyncStatus;
import retrofit2.Call;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

/**
 * Retrofit interface defining the Avni server API endpoints.
 * Handles all HTTP communication with the Avni backend.
 */
public interface AvniApiClient {
    
    /**
     * Get sync details including entity timestamps and user permissions
     */
    @POST("v2/syncDetails")
    Call<SyncDetailsResponse> getSyncDetails(
            @Body List<EntitySyncStatus> entitySyncStatuses,
            @Query("includeUserSubjectType") boolean includeUserSubjectType,
            @Query("deviceId") String deviceId
    );
    
    /**
     * Get user information and permissions
     */
    @GET("me")
    Call<JsonNode> getUserInfo();
    
    /**
     * Generic endpoint for fetching entities with pagination
     */
    @GET("{entityPath}")
    Call<PagedResponse<JsonNode>> getEntities(
            @Path("entityPath") String entityPath,
            @Query("lastModifiedDateTime") String lastModifiedDateTime,
            @Query("now") String now,
            @Query("size") int size,
            @Query("page") int page,
            @QueryMap Map<String, Object> additionalParams
    );
    
    /**
     * Generic endpoint for posting entities
     */
    @POST("{entityPath}")
    Call<JsonNode> postEntity(
            @Path("entityPath") String entityPath,
            @Body JsonNode entity
    );
    
    /**
     * Get signed URL for media download
     */
    @GET("media/signedUrl")
    Call<String> getMediaDownloadUrl(@Query("url") String s3Key);
    
    /**
     * Get signed URL for media upload
     */
    @GET("media/uploadUrl/{fileName}")
    Call<String> getMediaUploadUrl(@Path("fileName") String fileName);
    
    /**
     * Upload media file to presigned URL
     */
    @PUT
    Call<Void> uploadMedia(
            @Url String uploadUrl,
            @Body okhttp3.RequestBody fileBody,
            @Header("Content-Type") String contentType
    );
    
    /**
     * Download media file from URL
     */
    @GET
    Call<okhttp3.ResponseBody> downloadMedia(@Url String downloadUrl);
    
    /**
     * Response wrapper for sync details
     */
    class SyncDetailsResponse {
        private List<SyncDetail> syncDetails;
        private String nowMinus10Seconds;
        private String now;
        
        public List<SyncDetail> getSyncDetails() {
            return syncDetails;
        }
        
        public void setSyncDetails(List<SyncDetail> syncDetails) {
            this.syncDetails = syncDetails;
        }
        
        public String getNowMinus10Seconds() {
            return nowMinus10Seconds;
        }
        
        public void setNowMinus10Seconds(String nowMinus10Seconds) {
            this.nowMinus10Seconds = nowMinus10Seconds;
        }
        
        public String getNow() {
            return now;
        }
        
        public void setNow(String now) {
            this.now = now;
        }
    }
    
    /**
     * Individual sync detail for an entity type
     */
    class SyncDetail {
        private String entityName;
        private String entityTypeUuid;
        private String loadedSince;
        private String uuid;
        
        public String getEntityName() {
            return entityName;
        }
        
        public void setEntityName(String entityName) {
            this.entityName = entityName;
        }
        
        public String getEntityTypeUuid() {
            return entityTypeUuid;
        }
        
        public void setEntityTypeUuid(String entityTypeUuid) {
            this.entityTypeUuid = entityTypeUuid;
        }
        
        public String getLoadedSince() {
            return loadedSince;
        }
        
        public void setLoadedSince(String loadedSince) {
            this.loadedSince = loadedSince;
        }
        
        public String getUuid() {
            return uuid;
        }
        
        public void setUuid(String uuid) {
            this.uuid = uuid;
        }
    }
    
    /**
     * Generic paged response wrapper
     */
    class PagedResponse<T> {
        private List<T> content;
        private Map<String, List<T>> _embedded;
        private PageInfo page;
        
        public List<T> getContent() {
            return content;
        }
        
        public void setContent(List<T> content) {
            this.content = content;
        }
        
        public Map<String, List<T>> get_embedded() {
            return _embedded;
        }
        
        public void set_embedded(Map<String, List<T>> _embedded) {
            this._embedded = _embedded;
        }
        
        public PageInfo getPage() {
            return page;
        }
        
        public void setPage(PageInfo page) {
            this.page = page;
        }
        
        /**
         * Get the actual entity list from either content or _embedded
         */
        public List<T> getEntities(String resourceName) {
            if (content != null && !content.isEmpty()) {
                return content;
            }
            if (_embedded != null && _embedded.containsKey(resourceName)) {
                return _embedded.get(resourceName);
            }
            return List.of();
        }
    }
    
    /**
     * Page information for paginated responses
     */
    class PageInfo {
        private int number;
        private int size;
        private int totalPages;
        private long totalElements;
        
        public int getNumber() {
            return number;
        }
        
        public void setNumber(int number) {
            this.number = number;
        }
        
        public int getSize() {
            return size;
        }
        
        public void setSize(int size) {
            this.size = size;
        }
        
        public int getTotalPages() {
            return totalPages;
        }
        
        public void setTotalPages(int totalPages) {
            this.totalPages = totalPages;
        }
        
        public long getTotalElements() {
            return totalElements;
        }
        
        public void setTotalElements(long totalElements) {
            this.totalElements = totalElements;
        }
    }
}