package com.yem.hlm.backend.viewer3d.service;

import java.util.UUID;

public class Project3dModelNotFoundException extends RuntimeException {
    public Project3dModelNotFoundException(UUID projetId) {
        super("No 3D model found for project " + projetId);
    }
}
