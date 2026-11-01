package com.tommy.flashsale.repository;

import com.tommy.flashsale.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock,Long> {
}
