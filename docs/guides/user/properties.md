# Properties — User Guide

This guide covers creating and managing properties in the catalogue, understanding property statuses, uploading media, and soft-deleting properties.

## Table of Contents

1. [Property Types](#property-types)
2. [Property Status Lifecycle](#property-status-lifecycle)
3. [Creating a Property](#creating-a-property)
4. [Editing a Property](#editing-a-property)
5. [Uploading Media](#uploading-media)
6. [Changing Property Status](#changing-property-status)
7. [Deleting a Property](#deleting-a-property)
8. [Searching and Filtering Properties](#searching-and-filtering-properties)

---

## Property Types

The platform supports nine property types:

| Type | Description |
|------|-------------|
| VILLA | Detached house with land |
| APPARTEMENT | Flat / apartment in a building |
| DUPLEX | Two-level apartment |
| STUDIO | Studio apartment |
| T2 | Two-room apartment |
| T3 | Three-room apartment |
| COMMERCE | Commercial unit |
| LOT | Land lot |
| TERRAIN_VIERGE | Raw land / undeveloped plot |

Each type has specific required fields (see below).

---

## Property Status Lifecycle

| Status | Meaning |
|--------|---------|
| DRAFT | Being prepared, not yet visible to prospects |
| ACTIVE | Available on the market |
| RESERVED | Has an active deposit or reservation |
| SOLD | Transaction completed |
| WITHDRAWN | Removed from market without being sold |
| ARCHIVED | Historical record, no longer actively managed |

**Typical flow:** DRAFT → ACTIVE → RESERVED → SOLD

A property is automatically set to RESERVED when a deposit or reservation is created, and to SOLD when a sale contract is signed.

---

## Creating a Property

**Required role:** Admin or Manager

1. Go to **Properties** in the sidebar.
2. Click **New Property**.
3. Select the **Project** this property belongs to.
4. Select the **Property Type**.
5. Fill in the required fields for the type:

### VILLA

| Field | Required |
|-------|---------|
| Reference number | Yes |
| Price | Yes |
| Surface area (m²) | Yes |
| Land area (m²) | Yes |
| Bedrooms | Yes |
| Bathrooms | Yes |
| Description | No |

### APPARTEMENT

| Field | Required |
|-------|---------|
| Reference number | Yes |
| Price | Yes |
| Surface area (m²) | Yes |
| Floor number | Yes |
| Bedrooms | Yes |
| Bathrooms | Yes |
| Description | No |

### LOT / TERRAIN_VIERGE

| Field | Required |
|-------|---------|
| Reference number | Yes |
| Price | Yes |
| Land area (m²) | Yes |

6. Set the initial **status** (usually DRAFT or ACTIVE).
7. Click **Save**.

---

## Editing a Property

**Required role:** Admin or Manager

1. Open the property record.
2. Click **Edit**.
3. Update the fields.
4. Click **Save**.

You cannot edit a property's type after creation. If the type is wrong, create a new property.

---

## Uploading Media

**Required role:** Admin or Manager

1. Open the property record.
2. Click the **Media** tab.
3. Click **Upload File**.
4. Select an image (JPEG, PNG, WebP) or document (PDF).
   - Maximum file size: 10 MB per file.
5. The file is uploaded and appears in the media gallery.

To delete a media file, click the **Delete** icon next to it in the gallery.

All users (including Agents) can view and download media files. Only Admins and Managers can upload or delete them.

---

## Changing Property Status

**Required role:** Admin or Manager

Status transitions happen automatically when:
- A deposit is created → property moves to RESERVED
- A sale contract is signed → property moves to SOLD

To manually change status (e.g., to WITHDRAWN or ARCHIVED):
1. Open the property record.
2. Click **Change Status**.
3. Select the new status.
4. Click **Confirm**.

---

## Deleting a Property

**Required role:** Admin only

Properties are never permanently deleted. Instead, "deleting" a property marks it as **soft-deleted**:
- The property is hidden from all lists and search results.
- Historical references (deposits, contracts, media) are preserved.
- The deletion is reversible by contacting your system administrator.

To delete a property:
1. Open the property record.
2. Click **Delete** (visible only to Admins).
3. Confirm the action.

---

## Searching and Filtering Properties

Use the filter bar at the top of the Properties list:

| Filter | Options |
|--------|---------|
| Search | Property reference or description |
| Project | Select a specific project |
| Type | VILLA, APPARTEMENT, LOT, etc. |
| Status | DRAFT, ACTIVE, RESERVED, SOLD, WITHDRAWN, ARCHIVED |
| Price range | Minimum and maximum price |

Deleted properties are never shown in search results.
