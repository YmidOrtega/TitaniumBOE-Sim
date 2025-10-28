# BOE Protocol Specification — Quick Reference (Validated from PDF)

## Message Structure

```
[StartOfMessage: 2 bytes][MessageLength: 2 bytes][Payload: variable]
```

* **StartOfMessage**: Always `0xBA 0xBA`
* **MessageLength**:
  Number of bytes **for the message**, **including this field (2 bytes)** but **not including** the two bytes for the `StartOfMessage` field.

  > “Number of bytes for the message, including this field but not including the two bytes for the StartOfMessage field.”
* **Payload**: `MessageType` + message body fields
* **Byte order**: All multi-byte fields are **Little Endian**

---

## Message Types (Confirmed)

| Message Type     | Code (`hex`) | Direction       | Description          |
| ---------------- | ------------ | --------------- | -------------------- |
| Login Request    | `0x37`       | Client → Server | Initiates login      |
| Logout Request   | `0x02`       | Client → Server | Terminates session   |
| Client Heartbeat | `0x03`       | Client → Server | Keepalive signal     |
| Login Response   | `0x07`       | Server → Client | Login acknowledgment |
| Server Heartbeat | `0x04`       | Server → Client | Server keepalive     |

---

## Login Request Message (`0x37`)

### Structure

```
Offset | Size | Field           | Type         | Notes
-------|------|-----------------|--------------|--------------------------------
0      | 2    | StartOfMessage  | Binary       | Always 0xBA 0xBA
2      | 2    | MessageLength   | Binary (LE)  | Bytes for message incl. this field, excl. StartOfMessage → 27
4      | 1    | MessageType     | Binary       | 0x37
5      | 1    | MatchingUnit    | Binary       | Typically 0
6      | 4    | SequenceNumber  | Binary (LE)  | Incremental counter
10     | 4    | SessionSubID    | Alphanumeric | Space-padded (0x20)
14     | 4    | Username        | Alphanumeric | Space-padded (0x20)
18     | 10   | Password        | Alphanumeric | Space-padded (0x20)
28     | 1    | NumParamGroups  | Binary       | Usually 0
```

✅ **Payload (from MessageType onward)** = 25 bytes
✅ **MessageLength** = 25 (payload) + 2 (itself) = **27 bytes**
✅ **Total bytes transmitted** = 2 (StartOfMessage) + 27 = **29 bytes**

---

## Logout Request Message (`0x02`)

### Structure

```
Offset | Size | Field           | Type         | Notes
-------|------|-----------------|--------------|--------------------------
0      | 2    | StartOfMessage  | Binary       | 0xBA 0xBA
2      | 2    | MessageLength   | Binary (LE)  | Includes this field (8 total)
4      | 1    | MessageType     | Binary       | 0x02
5      | 1    | MatchingUnit    | Binary       | Typically 0
6      | 4    | SequenceNumber  | Binary (LE)  | Incremental counter
```

✅ Payload (MessageType → SequenceNumber) = 6 bytes
✅ MessageLength = 6 + 2 = **8**
✅ Total message = 2 + 8 = **10 bytes**

---

## Client Heartbeat Message (`0x03`)

### Structure

```
Offset | Size | Field           | Type         | Notes
-------|------|-----------------|--------------|--------------------------
0      | 2    | StartOfMessage  | Binary       | 0xBA 0xBA
2      | 2    | MessageLength   | Binary (LE)  | Includes this field (8 total)
4      | 1    | MessageType     | Binary       | 0x03
5      | 1    | MatchingUnit    | Binary       | Typically 0
6      | 4    | SequenceNumber  | Binary (LE)  | Incremental counter
```

✅ Payload = 6 bytes
✅ MessageLength = 8
✅ Total Message = 10 bytes

---

## Protocol Rules

### Sequence Numbers

* Sequential across **all** message types.
* Start at **1** after successful login.
* Increment by **1** for every sent message (Login, Logout, Heartbeat, Orders, etc.).
* Use a **thread-safe counter (AtomicInteger)** if multithreaded.

### String Padding

* All alphanumeric fields are **space-padded** (`0x20`), **not null-padded**.
* Left-aligned, right-padded.
* Example: `"ABC"` in 4-byte field → `[0x41 0x42 0x43 0x20]`.

### Heartbeat Behavior

* Default client heartbeat interval: **10 seconds** (configurable).
* Server may reply with `Server Heartbeat (0x04)`.
* Missed heartbeats beyond timeout ⇒ connection closed by server.

### Matching Unit

* For client-to-server messages → typically `0`.
* For server responses → set by Cboe if applicable.

---

## Message Length Calculation Examples

### Example 1 — Login Request

```
Components:
MessageType (1) + MatchingUnit (1) + SequenceNumber (4) +
SessionSubID (4) + Username (4) + Password (10) + NumParamGroups (1)
= 25 bytes payload
```

* MessageLength = 25 + 2 = **27**
* Total Message = 2 (StartOfMessage) + 27 = **29 bytes**

---

### Example 2 — Logout / Heartbeat

```
Components:
MessageType (1) + MatchingUnit (1) + SequenceNumber (4) = 6 bytes payload
```

* MessageLength = 6 + 2 = **8**
* Total Message = 2 (StartOfMessage) + 8 = **10 bytes**

---

## Common Pitfalls

| ✅ Correct                   | ❌ Incorrect                   |
| --------------------------- | ----------------------------- |
| MessageLength = payload + 2 | MessageLength = payload only  |
| Excludes StartOfMessage     | Includes StartOfMessage       |
| Padding with spaces (0x20)  | Padding with nulls (0x00)     |
| Global incremental sequence | Independent counters per type |
| Atomic counter              | Hardcoded numbers             |

---

## Testing Checklist

* [ ] StartOfMessage always `0xBA 0xBA`
* [ ] MessageLength = payload + 2
* [ ] StartOfMessage **not** included in MessageLength
* [ ] Numeric fields in **Little Endian**
* [ ] Strings space-padded (0x20)
* [ ] Sequence increments correctly
* [ ] Heartbeats every 10 s
* [ ] Deserialization verified
* [ ] Edge cases (min/max message sizes) tested

---

## References

* **Source:** *US_Options_BOE_Specification.pdf* (Cboe Global Markets)
* **Relevant sections:**

    * § 5.1 “Message Header” — defines MessageLength
    * § 5.3 “Login Request”
    * § 5.4 “Logout Request”
    * § 5.5 “Client/Server Heartbeat”
* **Quote:**

  > “Number of bytes for the message, including this field but not including the two bytes for the StartOfMessage field.”
* **Byte Order:** Little Endian throughout

---