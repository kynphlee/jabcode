# History Screen UI/UX Audit

## Screenshot Reference
`Screenshot_20260124_205640_QR Scanner.jpg`

---

## Screen Overview

The History screen displays previously scanned barcodes. The screenshot shows an empty state with placeholder text and a secondary action bar for data management.

---

## Layout Structure

```
+---------------------------------------------+
| [Scan]  [Create]  [History]  [Settings]    |  <- Primary Navigation (Top)
+---------------------------------------------+
|  0      Export    Import     Trash  Search |  <- Secondary Action Bar
+---------------------------------------------+
|                                             |
|                                             |
|                                             |
|                                             |
|       Your scans will be listed here.      |  <- Empty State
|                                             |
|                                             |
|                                             |
|                                             |
+---------------------------------------------+
```

---

## Component Breakdown

### 1. Primary Navigation Bar

Same as other screens, with "History" tab active (orange underline).

### 2. Secondary Action Bar

| Element | Type | Function |
|---------|------|----------|
| Count ("0") | Text | Total scan count |
| Export | Text button | Export history data |
| Import | Text button | Import history data |
| Trash icon | Icon button | Delete selected/all |
| Search icon | Icon button | Search history |

**Specifications:**

| Property | Value |
|----------|-------|
| Bar height | ~48dp |
| Background | Black (#000000) |
| Text color | White (#FFFFFF) |
| Icon color | White (#FFFFFF) |
| Padding horizontal | 16dp |
| Item spacing | Even distribution |

### 3. Empty State

**Content:**
- Text: "Your scans will be listed here."
- Centered vertically and horizontally
- Text color: Gray (#B3B3B3)
- Text size: 16sp

**Design Pattern:**
- No icon (minimal approach)
- Single line of instructional text
- Subtle, non-intrusive

---

## History List Item (When Populated)

Expected structure for each scan entry:

```
+---------------------------------------------+
| [Icon]  Content preview...                  |
|         Barcode type | Date/Time       [>] |
+---------------------------------------------+
```

### List Item Components

| Element | Description |
|---------|-------------|
| Icon | Barcode type icon (QR, JABCode, etc.) |
| Content preview | First line of decoded data |
| Barcode type | "QR Code", "JABCode 8-color", etc. |
| Timestamp | "Today 3:45 PM" or "Jan 24, 2026" |
| Chevron | Navigation indicator |

### List Item Specifications

| Property | Value |
|----------|-------|
| Item height | ~72dp |
| Icon size | 40dp |
| Primary text | 16sp, white |
| Secondary text | 14sp, gray |
| Padding | 16dp horizontal |
| Divider | 1dp, #333333 |

---

## Interaction Patterns

### Export
1. Tap "Export"
2. Choose format (CSV, JSON, etc.)
3. System share sheet or file save dialog
4. Confirmation toast

### Import
1. Tap "Import"
2. File picker opens
3. Select compatible file
4. Confirmation with count imported
5. List refreshes

### Delete
1. Tap trash icon
2. Confirmation dialog: "Delete all history?"
3. Options: "Cancel" / "Delete"
4. If confirmed, clear history

### Search
1. Tap search icon
2. Search bar appears (replaces action bar)
3. Type to filter
4. Results update in real-time
5. "X" to clear/close search

### List Item Tap
1. Tap item
2. Navigate to detail view
3. Show full content, actions (copy, share, open)

### Swipe Actions (Optional)
- Swipe left: Delete single item
- Swipe right: Share item

---

## States

### Empty State (Shown)
- Placeholder text centered
- Action bar still visible
- Export/Import still functional (for import)

### Populated State
- Scrollable list
- Count shows total items
- All actions enabled

### Search Active
- Search bar replaces count
- Filtered results
- "No results" if empty search

### Selection Mode (Optional)
- Long-press to select
- Multi-select with checkboxes
- Batch delete enabled

---

## Data Model

```kotlin
data class ScanHistoryItem(
    val id: Long,
    val content: String,
    val barcodeType: BarcodeType,
    val colorMode: Int?,  // For JABCode
    val timestamp: Instant,
    val thumbnailPath: String?  // Optional barcode image
)

enum class BarcodeType {
    QR_CODE,
    JABCODE,
    DATA_MATRIX,
    CODE_128,
    // ...
}
```

---

## Export/Import Formats

### Export Options

| Format | Use Case |
|--------|----------|
| CSV | Spreadsheet import |
| JSON | Backup/restore |
| Plain text | Simple list |

### CSV Structure

```csv
id,content,type,color_mode,timestamp
1,"https://example.com",QR_CODE,,2026-01-24T15:45:00Z
2,"Hello JABCode",JABCODE,8,2026-01-24T16:00:00Z
```

### JSON Structure

```json
{
  "version": 1,
  "exported": "2026-01-24T16:30:00Z",
  "items": [
    {
      "id": 1,
      "content": "https://example.com",
      "type": "QR_CODE",
      "timestamp": "2026-01-24T15:45:00Z"
    }
  ]
}
```

---

## JABCode Adaptations

### Additional Metadata for JABCode

| Field | Description |
|-------|-------------|
| Color mode | 4, 8, 16, 32, 64, 128 |
| ECC level | Error correction used |
| Symbol count | Multi-symbol codes |
| Data size | Bytes decoded |

### JABCode History Item Display

```
+---------------------------------------------+
| [JAB]  Hello JABCode World!                 |
|        JABCode 8-color | Today 4:00 PM  [>] |
+---------------------------------------------+
```

### Color Mode Badge

Small colored indicator showing the color mode:

```
[4] [8] [16] [32] [64] [128]
 ^--- Orange badge with number
```

---

## Accessibility

| Feature | Implementation |
|---------|----------------|
| Content descriptions | "Scan history, 0 items" |
| Empty state | Announced by screen reader |
| List navigation | Focus moves through items |
| Actions | "Export history", "Import history", etc. |

---

## Implementation Notes

### Android Components

```kotlin
// Layout structure
CoordinatorLayout
+-- TabLayout (primary navigation)
+-- LinearLayout (secondary action bar)
|   +-- TextView (count)
|   +-- Button (export)
|   +-- Button (import)
|   +-- ImageButton (delete)
|   +-- ImageButton (search)
+-- RecyclerView (history list)
|   +-- HistoryAdapter
+-- TextView (empty state, visibility toggled)
```

### Room Database

```kotlin
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val barcodeType: String,
    val colorMode: Int?,
    val timestamp: Long
)

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ScanHistoryEntity>>
    
    @Query("SELECT * FROM scan_history WHERE content LIKE :query")
    fun search(query: String): Flow<List<ScanHistoryEntity>>
    
    @Insert
    suspend fun insert(item: ScanHistoryEntity)
    
    @Delete
    suspend fun delete(item: ScanHistoryEntity)
    
    @Query("DELETE FROM scan_history")
    suspend fun deleteAll()
}
```

---

## Metrics to Track

| Metric | Purpose |
|--------|---------|
| History size | Storage usage |
| Export frequency | Backup behavior |
| Search usage | Feature adoption |
| Delete patterns | Data retention |
| Item tap rate | Re-use of scans |

---

*Document created: 2026-01-24*
*Related: [index.md](index.md), [01-scan-screen-audit.md](01-scan-screen-audit.md), [02-create-screen-audit.md](02-create-screen-audit.md)*
