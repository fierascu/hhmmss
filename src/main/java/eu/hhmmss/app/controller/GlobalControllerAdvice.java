package eu.hhmmss.app.controller;

import eu.hhmmss.app.uploadingfiles.storage.BuildInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final BuildInfoService buildInfoService;

    @ModelAttribute("buildInfo")
    public BuildInfoService addBuildInfoToModel() {
        return buildInfoService;
    }
}
