# Projects, Buildings, And Properties Guide

This guide explains how the inventory side of the platform is organized.

## 1. Inventory Hierarchy

The platform can represent inventory at several levels:

```text
Project
  -> Immeuble
    -> Tranche
      -> Property
```

Not every workflow uses every level, but this is the target professional structure.

## 2. Projects

Projects represent the real-estate program or development.

Typical actions:

- create a project
- maintain project identity and professional details
- review project KPI data
- organize properties underneath the project

## 3. Immeubles

Use immeubles when a project contains multiple buildings or blocks.

Benefits:

- clearer inventory organization
- better sales reporting
- easier property filtering

## 4. Tranches

Use tranches when the project is delivered or marketed in phases.

Typical use cases:

- phased release
- tranche-specific monitoring
- controlled generation or progression workflows

## 5. Properties

A property is the sellable unit or lot.

Typical data includes:

- type
- reference
- price
- status
- location
- project association
- technical characteristics
- media

## 6. Property Statuses

Main statuses:

- `DRAFT`
- `ACTIVE`
- `RESERVED`
- `SOLD`
- `WITHDRAWN`
- `ARCHIVED`

Important rule:

- `RESERVED` and `SOLD` are usually driven by business workflow, not just editorial status changes

## 7. Import And Media

Authorized users can:

- import properties in bulk
- upload property media
- maintain visual and descriptive quality

Best practice:

- keep reference codes clean and unique
- use consistent project and building naming
- attach enough media for sales and portal clarity
