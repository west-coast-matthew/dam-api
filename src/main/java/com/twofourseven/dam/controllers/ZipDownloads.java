package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.AssetDownloadService;
import com.twofourseven.dam.services.AssetDownloadService.DownloadBundle;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

/** Turns a prepared {@link DownloadBundle} into a streamed zip attachment (spec 009). */
final class ZipDownloads {

    private ZipDownloads() {
    }

    static ResponseEntity<StreamingResponseBody> response(AssetDownloadService service, DownloadBundle bundle) {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(bundle.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .body(out -> service.writeZip(bundle, out));
    }
}
