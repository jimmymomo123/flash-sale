package com.tommy.flashsale.repository;

import com.tommy.flashsale.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order,Long> {
}
