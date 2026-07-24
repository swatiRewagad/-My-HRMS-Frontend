package com.hrms.cms.repository;

import com.hrms.cms.entity.Pincode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface PincodeRepository extends JpaRepository<Pincode, Long> {

    List<Pincode> findByPincode(String pincode);

    @Query("SELECT DISTINCT p.district FROM Pincode p WHERE p.state = :state ORDER BY p.district")
    List<String> findDistinctDistrictsByState(String state);

    @Query("SELECT DISTINCT p.state FROM Pincode p ORDER BY p.state")
    List<String> findDistinctStates();

    @Query("SELECT DISTINCT p.pincode FROM Pincode p WHERE p.district = :district")
    List<String> findPincodesByDistrict(String district);
}
