package org.avni.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import java.time.Instant;

@Entity
@Table(name = "video_telemetric")
@BatchSize(size = 100)
public class VideoTelemetric extends CHSBaseEntity {

    //the video progress time
    //start time
    @Column(name = "video_start_time")
    private Double videoStartTime;

    //the video progress time
    //end time
    @Column(name = "video_end_time")
    private Double videoEndTime;

    @Column(name = "player_close_time")
    private Instant playerCloseTime;

    @Column(name = "player_open_time")
    private Instant playerOpenTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name = "video_id")
    private Video video;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotNull
    @JoinColumn(name = "user_id")
    private User user;

    @Column
    private Long organisationId;

    @Column(name="created_datetime")
    private Instant createdDatetime;

    public Double getVideoStartTime() {
        return videoStartTime;
    }

    public void setVideoStartTime(Double videoStartTime) {
        this.videoStartTime = videoStartTime;
    }

    public Double getVideoEndTime() {
        return videoEndTime;
    }

    public void setVideoEndTime(Double videoEndTime) {
        this.videoEndTime = videoEndTime;
    }

    public DateTime getPlayerCloseTime() {
        return DateTimeUtil.toJodaDateTime(playerCloseTime);
    }

    public void setPlayerCloseTime(DateTime playerCloseTime) {
        this.playerCloseTime = DateTimeUtil.toInstant(playerCloseTime);
    }

    public DateTime getPlayerOpenTime() {
        return DateTimeUtil.toJodaDateTime(playerOpenTime);
    }

    public void setPlayerOpenTime(DateTime playerOpenTime) {
        this.playerOpenTime = DateTimeUtil.toInstant(playerOpenTime);
    }

    public Video getVideo() {
        return video;
    }

    public void setVideo(Video video) {
        this.video = video;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Long getOrganisationId() {
        return organisationId;
    }

    public void setOrganisationId(Long organisationId) {
        this.organisationId = organisationId;
    }

    public DateTime getCreatedDatetime() {
        return DateTimeUtil.toJodaDateTime(createdDatetime);
    }

    public void setCreatedDatetime(DateTime createdDatetime) {
        this.createdDatetime = DateTimeUtil.toInstant(createdDatetime);
    }
}
