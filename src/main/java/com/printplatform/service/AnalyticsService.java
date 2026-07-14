package com.printplatform.service;

import com.printplatform.model.PageView;
import com.printplatform.repository.PageViewRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AnalyticsService {

    private final PageViewRepository pageViewRepository;

    public AnalyticsService(PageViewRepository pageViewRepository) {
        this.pageViewRepository = pageViewRepository;
    }

    public void recordPageView(String path, UUID userId, String sessionId, String referrer) {
        PageView view = new PageView();
        view.setPath(path);
        view.setUserId(userId);
        view.setSessionId(sessionId);
        view.setReferrer(referrer);
        pageViewRepository.save(view);
    }
}
