package com.apsonic.backend.modules.navigation.repository;

import com.apsonic.backend.modules.navigation.domain.MenuKey;
import com.apsonic.backend.modules.navigation.domain.NavigationMenu;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NavigationMenuRepository extends JpaRepository<NavigationMenu, Long> {
    Optional<NavigationMenu> findByMenuKey(MenuKey menuKey);
}
