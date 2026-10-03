package com.watchcue;

import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Instant;
import java.util.*;

@SpringBootApplication
@EnableScheduling
public class WatchCueApplication {
  public static void main(String[] args) { SpringApplication.run(WatchCueApplication.class, args); }
}

@Entity
@Table(name="watch_items")
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
class WatchItem {
  @Id UUID id=UUID.randomUUID();
  @Column(nullable=false) String title;
  @Column(name="item_type",nullable=false) String itemType;
  @Column(name="source_url",columnDefinition="text") String sourceUrl;
  @Column(name="why_saved") String whySaved;
  @Column(name="youtube_video_id") String youtubeVideoId;
  @Column(name="youtube_title") String youtubeTitle;
  @Column(name="youtube_thumbnail",columnDefinition="text") String youtubeThumbnail;
  @Column(nullable=false) String priority="INTERESTED";
  @Column(nullable=false) String status="UNWATCHED";
  @Column(name="created_at",nullable=false) Instant createdAt=Instant.now();
  @Column(name="watched_at") Instant watchedAt;
  @Column(name="snoozed_until") Instant snoozedUntil;
}

interface WatchItemRepository extends JpaRepository<WatchItem,UUID> {}

@Entity
@Table(name="tv_settings")
class TvSettings {
  @Id Long id=1L;
  boolean enabled=true;
  @Column(name="notify_on_tv_start") boolean notifyOnTvStart=true;
  @Column(name="periodic_enabled") boolean periodicEnabled=false;
  @Column(name="period_minutes") int periodMinutes=180;
  @Column(name="max_items") int maxItems=5;
  @Column(name="only_unwatched") boolean onlyUnwatched=true;
  @Column(name="device_id") String deviceId="living-room-tv";
  @Column(name="last_periodic_sent_at") Instant lastPeriodicSentAt;
}
interface TvSettingsRepository extends JpaRepository<TvSettings,Long> {}

@RestController
@RequestMapping("/api/v1/watch-items")
class WatchItemController {
  private final WatchItemRepository repo;
  private final RestClient rest=RestClient.create();
  @Value("${watchcue.youtube.api-key:}") String youtubeKey;
  WatchItemController(WatchItemRepository repo){this.repo=repo;}

  @GetMapping List<WatchItem> list(@RequestParam(defaultValue="true") boolean includeWatched){
    return repo.findAll().stream().filter(i->includeWatched||!"WATCHED".equals(i.status))
      .sorted(Comparator.comparing((WatchItem i)->i.createdAt).reversed()).toList();
  }

  record CreateRequest(String title,String itemType,String sourceUrl,String whySaved,String priority,Boolean autoFindTrailer){}
  @PostMapping WatchItem add(@RequestBody CreateRequest r){
    WatchItem i=new WatchItem(); i.title=r.title(); i.itemType=Optional.ofNullable(r.itemType()).orElse("MOVIE");
    i.sourceUrl=r.sourceUrl(); i.whySaved=r.whySaved(); i.priority=Optional.ofNullable(r.priority()).orElse("INTERESTED");
    if(Boolean.TRUE.equals(r.autoFindTrailer())) findTrailer(i);
    return repo.save(i);
  }

  @PostMapping("/{id}/watched") WatchItem watched(@PathVariable UUID id){WatchItem i=get(id);i.status="WATCHED";i.watchedAt=Instant.now();return repo.save(i);}
  @PostMapping("/{id}/unwatched") WatchItem unwatched(@PathVariable UUID id){WatchItem i=get(id);i.status="UNWATCHED";i.watchedAt=null;return repo.save(i);}
  @PostMapping("/{id}/trailer/refresh") WatchItem trailer(@PathVariable UUID id){
    WatchItem i=get(id);
    findTrailer(i);
    return repo.save(i);
  }
  @DeleteMapping("/{id}") void delete(@PathVariable UUID id){repo.deleteById(id);}
  private WatchItem get(UUID id){return repo.findById(id).orElseThrow();}

  @SuppressWarnings("unchecked")
  private void findTrailer(WatchItem i){
    if(youtubeKey==null||youtubeKey.isBlank()) {
      throw new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
        "YouTube trailer search is not configured. Add YOUTUBE_API_KEY to the backend environment."
      );
    }
    try{
      String url="https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=1&q="+
        java.net.URLEncoder.encode(i.title+" official trailer",java.nio.charset.StandardCharsets.UTF_8)+"&key="+youtubeKey;
      Map<String,Object> body=rest.get().uri(url).retrieve().body(Map.class);
      List<Map<String,Object>> items=body==null?null:(List<Map<String,Object>>)body.get("items");
      if(items==null||items.isEmpty()) {
        throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.NOT_FOUND,
          "No embeddable YouTube trailer found for "+i.title
        );
      }
      Map<String,Object> first=items.get(0), id=(Map<String,Object>)first.get("id"), snippet=(Map<String,Object>)first.get("snippet");
      i.youtubeVideoId=(String)id.get("videoId");
      i.youtubeTitle=(String)snippet.get("title");
      Map<String,Object> thumbs=(Map<String,Object>)snippet.get("thumbnails"), medium=(Map<String,Object>)thumbs.get("medium");
      i.youtubeThumbnail=medium==null?null:(String)medium.get("url");
    } catch(org.springframework.web.server.ResponseStatusException e) {
      throw e;
    } catch(Exception e) {
      throw new org.springframework.web.server.ResponseStatusException(
        org.springframework.http.HttpStatus.BAD_GATEWAY,
        "YouTube trailer search failed: "+e.getMessage(),
        e
      );
    }
  }
}

@RestController
@RequestMapping("/api/v1/tv")
class TvController {
  private final TvSettingsRepository settings; private final WatchItemRepository items; private final RestClient rest=RestClient.create();
  @Value("${watchcue.tv.notification-url:}") String tvUrl;
  @Value("${watchcue.tv.api-key:}") String tvKey;
  TvController(TvSettingsRepository settings,WatchItemRepository items){this.settings=settings;this.items=items;}
  private TvSettings s(){return settings.findById(1L).orElseGet(()->settings.save(new TvSettings()));}
  @GetMapping("/settings") TvSettings getSettings(){return s();}
  @PutMapping("/settings") TvSettings save(@RequestBody TvSettings in){in.id=1L;return settings.save(in);}
  record Online(String deviceId){}
  @PostMapping("/online") Map<String,Object> online(@RequestBody Online r){TvSettings s=s();return Map.of("notificationSent",s.enabled&&s.notifyOnTvStart&&Objects.equals(s.deviceId,r.deviceId())&&send(s));}
  @PostMapping("/send-watchlist") Map<String,Object> sendNow(){return Map.of("notificationSent",send(s()));}
  private boolean send(TvSettings s){
    if(!s.enabled||tvUrl==null||tvUrl.isBlank()) return false;
    List<WatchItem> list=items.findAll().stream().filter(i->!s.onlyUnwatched||!"WATCHED".equals(i.status)).limit(s.maxItems).toList();
    if(list.isEmpty()) return false;
    StringBuilder msg=new StringBuilder();int n=1;for(WatchItem i:list)msg.append(n++).append(". ").append(i.title).append("\n");
    try{
      var req=rest.post().uri(tvUrl).contentType(MediaType.APPLICATION_JSON);
      if(tvKey!=null&&!tvKey.isBlank()) req=req.header("X-API-Key",tvKey);
      req.body(Map.of("deviceId",s.deviceId,"title","WatchCue • Things to watch","message",msg.toString().trim())).retrieve().toBodilessEntity();
      return true;
    }catch(Exception e){return false;}
  }
}

@org.springframework.context.annotation.Configuration
class CorsConfig implements WebMvcConfigurer {
  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/api/**")
        .allowedOriginPatterns("http://localhost:*", "https://*.vercel.app")
        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
        .allowedHeaders("*")
        .exposedHeaders("*")
        .allowCredentials(false)
        .maxAge(3600);
  }
}
