# JFR-Tail CLI (TUI) User Guide

The JFR-Tail CLI provides a powerful Terminal User Interface (TUI) for real-time JVM monitoring. This guide covers the interactive tools and filtering capabilities.

## 🕹️ Navigation & Controls

| Key | Action |
|-----|--------|
| **Arrow Up/Down** | Select an event from the list. |
| **Enter** | Open a modal with the full JSON details of the selected event. |
| **Escape** | Close open modals or clear the active filter. |
| **C** | Clear the current event list and reset statistics. |
| **S** | Toggle the **Spring Boot Health** panel (requires Actuator integration). |
| **B** | Create a local **Incident Bundle** (saves events to a text file). |

---

## 🔍 Filtering System

JFR-Tail features a dual filtering system: **Category Toggles** and **Text Search**.

### 1. Category Toggles (Global)
These toggles work instantly and affect the entire stream. You can see their current status in the header.
- **`G`**: Toggle **GC** (Garbage Collection) events.
- **`L`**: Toggle **Locks** (Java Monitor / Thread Park) events.
- **`E`**: Toggle **Exceptions** (Exception Thrown) events.
- **`P`**: Toggle **CPU** (System/Process Load) events.

> [!TIP]
> Toggles are checked **before** text search. If you disable GC events with `G`, they will not appear even if you search for "GC".

### 2. Interactive Text Filter
Press **`F`** to enter filtering mode.
- Type any string to filter the list by **Event Type** or **Thread Name**.
- Press **Enter** or **Escape** to finish typing.
- The footer will display your active filter buffer.

---

## 🛠️ Command Mode (Exit & More)

To prevent accidental data loss or unexpected shutdowns, the CLI uses a deliberate command mode.

1. Press **`:`** (colon) to enter command mode.
2. Type one of the following commands:
   - `:q` or `:quit`: Safely exit the CLI.
   - `:exit`: Safely exit the CLI.
3. Press **Enter** to execute the command.

---

## 📊 Statistics Panel (Right Side)

- **GC Evt**: Total Garbage Collection events since last clear.
- **Excep**: Total Exceptions detected.
- **Locks**: Total lock contention events.
- **Heap Used/Committed**: Live memory usage from the target JVM.
- **Events/Sec**: A 20-second histogram showing the ingestion rate.

---

## 🛡️ Stability Note
Recent updates have fixed issues where pressing **Backspace** would crash the CLI in certain environments. You can now safely edit your filters and commands.
