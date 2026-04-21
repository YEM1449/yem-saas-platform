# GDPR And Privacy Compliance Guide

This guide explains how privacy requirements appear in the technical implementation.

## 1. Compliance Scope In The Platform

Privacy concerns are embedded in:

- `societe` configuration
- `contact` records
- `app_user` records
- retention schedulers
- export and anonymization flows

## 2. Data Subject Operations

Current capabilities include:

- export
- rectification support views
- anonymization
- privacy notice access
- retention automation

## 3. Technical Building Blocks

| Area | Example classes |
| --- | --- |
| contact privacy flows | `GdprController`, `GdprService` |
| member privacy flows | `UserGdprService` |
| retention automation | `DataRetentionScheduler` |
| company defaults | `Societe` compliance fields |

## 4. Engineering Rules

- treat privacy fields as first-class domain data
- do not strip audit or contract integrity without understanding legal consequences
- document any new personal-data field and its lifecycle
- review retention, export, and anonymization implications when adding a new entity

## 5. Product Rules To Preserve

- signed or legally necessary records can block destructive erasure
- company-level defaults influence contact retention behavior
- user-facing exports must stay scoped and safe

## 6. Change Checklist For Privacy-Relevant Work

If you add or change:

- a new personal-data field
- a new document flow
- a new portal-visible field
- a new export or reporting surface

then review:

- retention expectations
- anonymization impact
- role permissions
- documentation updates
