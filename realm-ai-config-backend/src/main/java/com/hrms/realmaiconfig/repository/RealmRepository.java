package com.hrms.realmaiconfig.repository;

import com.hrms.realmaiconfig.entity.Realm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RealmRepository extends JpaRepository<Realm, String> {
    List<Realm> findByStatus(String status);
}
