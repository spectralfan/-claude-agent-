package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingSessionBindingDTO;

import java.util.Optional;

public interface CodingSessionBindingService {

    Optional<CodingSessionBindingDTO> findBinding(String sessionId);
}
