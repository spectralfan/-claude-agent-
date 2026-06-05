package com.kama.jchatmind.coding.service;

import com.kama.jchatmind.coding.model.dto.CodingSkillDTO;

import java.util.List;
import java.util.Optional;

public interface CodingSkillService {

    List<CodingSkillDTO> listSkills();

    Optional<CodingSkillDTO> findById(String skillId);
}
