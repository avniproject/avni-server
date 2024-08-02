package org.avni.server.service.application;

import org.avni.server.application.menu.MenuItem;
import org.avni.server.common.BulkItemSaveException;
import org.avni.server.dao.application.MenuItemRepository;
import org.avni.server.service.NonScopeAwareService;
import org.avni.server.web.request.application.menu.MenuItemContract;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuItemService implements NonScopeAwareService {
    private final MenuItemRepository menuItemRepository;

    @Autowired
    public MenuItemService(MenuItemRepository menuItemRepository) {
        this.menuItemRepository = menuItemRepository;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return menuItemRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    @Transactional
    public List<MenuItem> findAll() {
        return menuItemRepository.findAll();
    }

    @Transactional
    public MenuItem save(MenuItem menuItem) {
        return menuItemRepository.save(menuItem);
    }

    @Transactional
    public MenuItem find(String uuid) {
        return menuItemRepository.findByUuid(uuid);
    }

    @Transactional
    public MenuItem find(Long id) {
        return menuItemRepository.findEntity(id);
    }

    @Transactional
    public void saveMenuItems(MenuItemContract[] menuItemContracts) {
        for (MenuItemContract contract : menuItemContracts) {
            try {
                MenuItem menuItem = this.find(contract.getUuid());
                this.save(MenuItemContract.toEntity(contract, menuItem));
            } catch (Exception e) {
                throw new BulkItemSaveException(contract, e);
            }
        }
    }
}
