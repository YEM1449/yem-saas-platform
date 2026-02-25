package com.yem.hlm.backend.project.service;

public class ProjectNameAlreadyExistsException extends RuntimeException {
    public ProjectNameAlreadyExistsException(String name) {
        super("A project with name '" + name + "' already exists in this tenant");
    }
}
