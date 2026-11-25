package com.twofourseven.dam.controllers;

import com.twofourseven.dam.services.TagService;
import com.twofourseven.dam.vo.TagVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Catalog tags (spec 023). Tags are assigned through the asset endpoints. */
@RestController
@RequestMapping("/tags")
public class TagController {

    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping
    public List<TagVO> list(@RequestParam UUID catalogId,
                            @RequestParam(required = false) String q,
                            @RequestParam(required = false) Integer limit) {
        return service.list(catalogId, q, limit);
    }
}
