package com.example.beyond.ordersystem.ordering.repository;

import com.example.beyond.ordersystem.member.controller.MemberController;
import com.example.beyond.ordersystem.member.domain.Member;
import com.example.beyond.ordersystem.ordering.domain.Ordering;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderingRepository extends JpaRepository<Ordering, Long> {
    List<Ordering> findByMember(Member member);
}
