package com.tommy.flashsale.repository;

import com.tommy.flashsale.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {
}
