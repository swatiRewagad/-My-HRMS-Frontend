package com.hrms.ecm.repository;

import com.hrms.ecm.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FolderRepository extends JpaRepository<Folder, Long> {
    List<Folder> findByParentIdIsNullOrderByNameAsc();
    List<Folder> findByParentIdOrderByNameAsc(Long parentId);
    List<Folder> findByVisibility(String visibility);
    long countByVisibility(String visibility);
}
