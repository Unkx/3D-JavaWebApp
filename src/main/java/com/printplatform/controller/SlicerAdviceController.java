package com.printplatform.controller;

import com.printplatform.dto.SlicerAdviceRequest;
import com.printplatform.dto.SlicerAdviceResponse;
import com.printplatform.service.SlicerAdviceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class SlicerAdviceController {

    private final SlicerAdviceService slicerAdviceService;

    public SlicerAdviceController(SlicerAdviceService slicerAdviceService) {
        this.slicerAdviceService = slicerAdviceService;
    }

    @PostMapping("/slicer-advice")
    public SlicerAdviceResponse getSlicerAdvice(@Valid @RequestBody SlicerAdviceRequest request) {
        return slicerAdviceService.getAdvice(request);
    }
}
