package com.apsonic.backend.modules.trash.service;

import com.apsonic.backend.modules.trash.dto.TrashItemResponse;
import java.util.List;

public interface TrashService {
    List<TrashItemResponse> getTrashItems(String resourceType);
    void restoreItem(String resourceType, String id);
    void deletePermanently(String resourceType, String id);
    void clearTrash(String resourceType);
}
