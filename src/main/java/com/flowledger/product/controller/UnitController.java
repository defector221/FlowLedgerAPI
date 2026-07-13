package com.flowledger.product.controller;

import com.flowledger.product.dto.UnitDtos.*;
import com.flowledger.product.service.UnitService;
import jakarta.validation.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/units")
public class UnitController {
    private final UnitService s;

    public UnitController(UnitService s) {
        this.s = s;
    }

    @GetMapping
    public List<Response> list() {
        return s.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Response create(@Valid @RequestBody Create d) {
        return s.create(d);
    }
}
