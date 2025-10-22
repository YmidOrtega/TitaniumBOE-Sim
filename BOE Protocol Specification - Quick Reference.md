# BOE Protocol Specification - Quick Reference

## Message Structure

### Header Format

```
[StartOfMessage: 2 bytes][MessageLength: 2 bytes][Payload: variable]
```

-  **StartOfMessage**: Always `0xBA 0xBA`
-  **MessageLength**: Includes itself (2 bytes) but NOT StartOfMessage
   -  Example: If payload is 25 bytes, MessageLength = 2 + 25 = 27
-  **Payload**: Message Type + Message Body

### All values are **Little Endian**

---

## Message Types (Confirmed from PDF)

| Message          | Type Code | Description               |
| ---------------- | --------- | ------------------------- |
| Login Request    | `0x37`    | Client → Server login     |
| Logout Request   | `0x02`    | Client → Server logout    |
| Client Heartbeat | `0x03`    | Client → Server keepalive |
| Login Response   | `0x07`    | Server → Client login ack |
| Server Heartbeat | `0x04`    | Server → Client keepalive |

---

## Login Request Message (0x37)

### Structure

```
Offset | Size | Field            | Type         | Notes
-------|------|------------------|--------------|---------------------------
0      | 2    | StartOfMessage   | Binary       | 0xBA 0xBA
2      | 2    | MessageLength    | Binary (LE)  | Includes itself
4      | 1    | MessageType      | Binary       | 0x37
5      | 1    | MatchingUnit     | Binary       | Usually 0
6      | 4    | SequenceNumber   | Binary (LE)  | Incremental counter
10     | 4    | SessionSubID     | Alphanumeric | Padded with spaces (0x20)
14     | 4    | Username         | Alphanumeric | Padded with spaces (0x20)
18     | 10   | Password         | Alphanumeric | Padded with spaces (0x20)
28     | 1    | NumParamGroups   | Binary       | Usually 0
```

**Total Payload**: 25 bytes  
**MessageLength**: 27 (2 + 25)  
**Total Message**: 29 bytes (2 + 27)

---

## Logout Request Message (0x02)

### Structure

```
Offset | Size | Field            | Type         | Notes
-------|------|------------------|--------------|---------------------------
0      | 2    | StartOfMessage   | Binary       | 0xBA 0xBA
2      | 2    | MessageLength    | Binary (LE)  | Always 8
4      | 1    | MessageType      | Binary       | 0x02
5      | 1    | MatchingUnit     | Binary       | Usually 0
6      | 4    | SequenceNumber   | Binary (LE)  | Incremental counter
```

**Total Payload**: 6 bytes  
**MessageLength**: 8 (2 + 6)  
**Total Message**: 10 bytes (2 + 8)

---

## Client Heartbeat Message (0x03)

### Structure

```
Offset | Size | Field            | Type         | Notes
-------|------|------------------|--------------|---------------------------
0      | 2    | StartOfMessage   | Binary       | 0xBA 0xBA
2      | 2    | MessageLength    | Binary (LE)  | Always 8
4      | 1    | MessageType      | Binary       | 0x03
5      | 1    | MatchingUnit     | Binary       | Usually 0
6      | 4    | SequenceNumber   | Binary (LE)  | Incremental counter
```

**Total Payload**: 6 bytes  
**MessageLength**: 8 (2 + 6)  
**Total Message**: 10 bytes (2 + 8)

---

## Important Rules

### Sequence Numbers

-  **MUST be sequential** across ALL message types
-  Start at 1 after login
-  Increment with each message sent (Login, Logout, Heartbeat, Orders, etc.)
-  Thread-safe: Use `AtomicInteger`

### String Padding

-  All alphanumeric fields padded with **spaces (0x20)**, NOT nulls
-  Left-aligned, right-padded
-  Example: "test" in 4-byte field → `[0x74 0x65 0x73 0x74]` or with padding `[0x74 0x65 0x73 0x20]`

### Heartbeat

-  Default interval: **10 seconds** (configurable)
-  Client must send heartbeats to keep session alive
-  Server may respond with Server Heartbeat (0x04)
-  Missing heartbeats may trigger timeout

### Matching Unit

-  Usually `0` for inbound (client → server) messages
-  May be set by server in responses

---

## Message Length Calculation Examples

### Example 1: Login Request

```
Components:
- StartOfMessage: 2 bytes (NOT in MessageLength)
- MessageLength field: 2 bytes (IN MessageLength)
- MessageType: 1 byte
- MatchingUnit: 1 byte
- SequenceNumber: 4 bytes
- SessionSubID: 4 bytes
- Username: 4 bytes
- Password: 10 bytes
- NumParamGroups: 1 byte

Payload = 1 + 1 + 4 + 4 + 4 + 10 + 1 = 25 bytes
MessageLength = 2 (itself) + 25 = 27 bytes
Total Message = 2 (StartOfMessage) + 27 = 29 bytes
```

### Example 2: Logout/Heartbeat

```
Components:
- StartOfMessage: 2 bytes (NOT in MessageLength)
- MessageLength field: 2 bytes (IN MessageLength)
- MessageType: 1 byte
- MatchingUnit: 1 byte
- SequenceNumber: 4 bytes

Payload = 1 + 1 + 4 = 6 bytes
MessageLength = 2 (itself) + 6 = 8 bytes
Total Message = 2 (StartOfMessage) + 8 = 10 bytes
```

---

## Common Pitfalls

❌ **Wrong**: MessageLength = payload only (missing the 2 bytes of length field)  
✅ **Correct**: MessageLength = 2 + payload

❌ **Wrong**: MessageLength includes StartOfMessage  
✅ **Correct**: MessageLength does NOT include StartOfMessage

❌ **Wrong**: Padding with null bytes (0x00)  
✅ **Correct**: Padding with spaces (0x20)

❌ **Wrong**: Independent sequence numbers per message type  
✅ **Correct**: Single global sequence counter for all messages

❌ **Wrong**: Hardcoded sequence numbers  
✅ **Correct**: Incremental AtomicInteger

---

## Testing Checklist

-  [ ] StartOfMessage is always `0xBA 0xBA`
-  [ ] MessageLength calculation is correct (2 + payload)
-  [ ] All numeric fields are Little Endian
-  [ ] Strings are padded with spaces (0x20)
-  [ ] Sequence numbers increment correctly
-  [ ] Heartbeats are sent at proper intervals
-  [ ] Messages can be deserialized correctly
-  [ ] Edge cases handled (minimum message, maximum message)

---

## References

-  Source: `US_Options_BOE_Specification.pdf`
-  Protocol: Binary Order Entry (BOE)
-  Version: Check PDF for specific version details
-  Byte Order: Little Endian throughout
