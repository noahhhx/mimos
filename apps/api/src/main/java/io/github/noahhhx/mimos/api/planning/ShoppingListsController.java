package io.github.noahhhx.mimos.api.planning;

import io.github.noahhhx.mimos.api.identity.CurrentUserService;
import io.github.noahhhx.mimos.planning.shopping.ShoppingListService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.openapitools.api.ShoppingListsApi;
import org.openapitools.model.ShoppingListItem;
import org.openapitools.model.ShoppingListItemPatch;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Shopping list endpoints over the core-planning domain (ADR-0003). */
@RestController
public class ShoppingListsController implements ShoppingListsApi {

    private final ShoppingListService shoppingListService;
    private final CurrentUserService currentUser;

    public ShoppingListsController(ShoppingListService shoppingListService, CurrentUserService currentUser) {
        this.shoppingListService = shoppingListService;
        this.currentUser = currentUser;
    }

    @Override
    public ResponseEntity<org.openapitools.model.ShoppingList> generateShoppingList(LocalDate startDate) {
        UUID profileId = currentUser.requireProfile().id();
        io.github.noahhhx.mimos.planning.shopping.ShoppingList generated =
                shoppingListService.generate(profileId, startDate);
        return ResponseEntity.ok(toApiList(generated));
    }

    @Override
    public ResponseEntity<org.openapitools.model.ShoppingList> getShoppingList(LocalDate startDate) {
        UUID profileId = currentUser.requireProfile().id();
        io.github.noahhhx.mimos.planning.shopping.ShoppingList list = shoppingListService
                .find(profileId, startDate)
                .orElseThrow(() -> new NoSuchElementException("no shopping list for week " + startDate));
        return ResponseEntity.ok(toApiList(list));
    }

    @Override
    public ResponseEntity<ShoppingListItem> updateShoppingListItem(
            LocalDate startDate, UUID itemId, ShoppingListItemPatch shoppingListItemPatch) {
        UUID profileId = currentUser.requireProfile().id();
        io.github.noahhhx.mimos.planning.shopping.ShoppingList.ShoppingListItem item =
                shoppingListService.updateChecked(profileId, startDate, itemId, shoppingListItemPatch.getChecked());
        return ResponseEntity.ok(toApiItem(item));
    }

    private static org.openapitools.model.ShoppingList toApiList(
            io.github.noahhhx.mimos.planning.shopping.ShoppingList list) {
        List<ShoppingListItem> items =
                list.items().stream().map(ShoppingListsController::toApiItem).toList();
        return new org.openapitools.model.ShoppingList()
                .startDate(list.startDate())
                .generatedAt(list.generatedAt().atOffset(ZoneOffset.UTC))
                .items(items);
    }

    private static ShoppingListItem toApiItem(
            io.github.noahhhx.mimos.planning.shopping.ShoppingList.ShoppingListItem item) {
        return new ShoppingListItem()
                .id(item.id())
                .name(item.name())
                .unit(item.unit())
                .quantity(item.quantity() == null ? null : BigDecimal.valueOf(item.quantity()))
                .category(item.category())
                .checked(item.checked());
    }
}
