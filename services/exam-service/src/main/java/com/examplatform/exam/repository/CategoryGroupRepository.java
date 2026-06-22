package com.examplatform.exam.repository;

import com.examplatform.exam.model.CategoryGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryGroupRepository extends JpaRepository<CategoryGroup, String> {
    List<CategoryGroup> findByActiveTrueOrderByDisplayOrderAsc();
    List<CategoryGroup> findAllByOrderByDisplayOrderAsc();
}
