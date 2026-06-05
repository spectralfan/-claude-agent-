package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingStackDTO;

import java.util.List;
import java.util.Optional;

public interface CodingStackService {

    List<CodingStackDTO> listStacks();

    Optional<CodingStackDTO> findById(String stackId);
}
