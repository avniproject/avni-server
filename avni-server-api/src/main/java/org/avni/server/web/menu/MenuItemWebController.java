package org.avni.server.web.menu;

import jakarta.transaction.Transactional;
import org.avni.server.application.menu.MenuItem;
import org.avni.server.dao.application.MenuItemRepository;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.application.MenuItemService;
import org.avni.server.web.AbstractController;
import org.avni.server.web.RestControllerResourceProcessor;
import org.avni.server.web.request.application.menu.MenuItemContract;
import org.avni.server.web.response.AvniEntityResponse;
import org.avni.server.web.response.MenuItemWebResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.hateoas.CollectionModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
public class MenuItemWebController extends AbstractController<MenuItem> implements RestControllerResourceProcessor<MenuItemWebResponse> {
    private final MenuItemRepository menuItemRepository;
    private final MenuItemService menuItemService;
    private final AccessControlService accessControlService;

    @Autowired
    public MenuItemWebController(MenuItemRepository menuItemRepository, MenuItemService menuItemService, AccessControlService accessControlService) {
        this.menuItemRepository = menuItemRepository;
        this.menuItemService = menuItemService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/web/menuItems", method = RequestMethod.POST)
    @Transactional
    public List<AvniEntityResponse> postMultiple(@RequestBody List<MenuItemContract> menuItemRequests) {
        accessControlService.checkPrivilege(PrivilegeType.EditApplicationMenu);
        return menuItemRequests.stream().map(this::post).collect(Collectors.toList());
    }

    @PostMapping("/web/menuItem")
    @Transactional
    public AvniEntityResponse post(@RequestBody MenuItemContract request) {
        accessControlService.checkPrivilege(PrivilegeType.EditApplicationMenu);
        MenuItem menuItem = menuItemService.save(MenuItemContract.toEntity(request, menuItemService.find(request.getUuid())));
        return new AvniEntityResponse(menuItem);
    }

    @PutMapping(value = "/web/menuItem/{id}")
    @Transactional
    public ResponseEntity<?> put(@PathVariable("id") Long id, @RequestBody MenuItemContract request) {
        accessControlService.checkPrivilege(PrivilegeType.EditApplicationMenu);
        MenuItem menuItem = menuItemService.save(MenuItemContract.toEntity(request, menuItemService.find(id)));
        return new ResponseEntity<>(menuItem, HttpStatus.OK);
    }

    @RequestMapping(value = "/web/menuItem/{id}", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public MenuItemContract getOne(@PathVariable("id") Long id) {
        MenuItem entity = menuItemService.find(id);
        return new MenuItemWebResponse(entity);
    }

    @RequestMapping(value = "/web/menuItem", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public CollectionModel<MenuItemWebResponse> getAll() {
        return wrapListAsPage(menuItemRepository.findAllByIsVoidedFalse().stream().map(MenuItemWebResponse::new).collect(Collectors.toList()));
    }

    @DeleteMapping(value = "/web/menuItem/{id}")
    @Transactional
    public ResponseEntity delete(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditApplicationMenu);
        menuItemRepository.voidEntity(id);
        return ResponseEntity.ok(null);
    }
}
