# BOE Protocol Specification — Quick Reference

**Source:** Cboe Titanium U.S. Options Binary Order Entry Specification  
**Version:** 2.11.90 · October 3, 2025  
**PDF on file:** `docs/US_Options_BOE_Specification.pdf`

---

## 1. Protocol Fundamentals

- All communication is via standard **TCP/IP**.
- All binary values are **Little Endian** (Intel x86 byte order), not network byte order.
- Each message is identified by a unique 1-byte **MessageType**.
- Terminology mirrors FIX protocol (Side, TimeInForce, OrdType, etc.) for familiarity.

---

## 2. Message Header (10 bytes — every message)

```
Offset  Len  Field            Notes
------  ---  ---------------  -------------------------------------------------
0       2    StartOfMessage   Always 0xBA 0xBA
2       2    MessageLength    Bytes for message incl. this field, excl. StartOfMessage
4       1    MessageType      1-byte message identifier (see §5)
5       1    MatchingUnit     Unit that created the message (see notes below)
6       4    SequenceNumber   32-bit LE sequence counter (see §6)
```

**MatchingUnit rules:**
- Session-level messages → always **0** in both directions.
- Member-to-Cboe (inbound) application messages → always **0**.
- Cboe-to-Member (outbound) application messages → set by Cboe to the matching unit.

**MessageLength formula:**
```
MessageLength = (total message bytes) - 2
             = MessageType(1) + MatchingUnit(1) + SequenceNumber(4) + body + 2
```
> Does **not** include the two `StartOfMessage` bytes.

---

## 3. Data Types

| Type | Byte Order | Size | Description |
|------|-----------|------|-------------|
| **Binary** | LE unsigned | context | `FE = 254`; `64 00 00 00 = 100` |
| **Signed Binary** | LE signed two's complement | context | `DF = -33`; `64 00 00 00 = +100` |
| **Binary Price** | LE signed | 8 bytes | 4 implied decimal places. `08 E2 01 00 00 00 00 00 = 123,400/10,000 = 12.34`. Negative prices supported (complex instruments). |
| **Short Binary Price** | LE signed | 4 bytes | 4 implied decimal places. `0C 30 00 00 = 12,300/10,000 = 1.23` |
| **Signed Binary Fee** | LE signed | 8 bytes | 5 implied decimal places. |
| **Alpha** | — | context | Uppercase A-Z and lowercase a-z only. **NUL (0x00) padded** right. |
| **Alphanumeric** | — | context | A-Z, a-z, 0-9. **NUL (0x00) padded** right. |
| **Text** | — | context | Printable ASCII. **NUL (0x00) padded** right. |
| **DateTime** | LE unsigned | 8 bytes | Nanoseconds past Unix epoch (UTC). `1,294,909,373,757,324,000 = 2011-01-13 09:02:53.757324 UTC` |
| **Date** | LE unsigned | 4 bytes | YYYYMMDD expressed as integer. |

> **All string/text fields are NUL-padded (0x00), left-aligned.**

---

## 4. Optional Fields and Bitfields

Messages such as **New Order** and **Modify Order** append optional fields after the required fields. Presence is controlled by bitfield bytes:

- A count byte (`NumberOfBitfields`) declares how many bitfield bytes follow.
- Each bit in a bitfield byte enables one optional field.
- Optional fields are appended in order: **first bitfield first, lowest-order bit first.**
- If an optional field is irrelevant in a given context, it is still present but with all bytes set to **0x00**.
- The set of optional fields that Cboe returns is negotiated at login time via the **Return Bitfields Parameter Group** in the Login Request.
- Each Cboe-to-Member message includes the bitfields echo so each message is self-describing (no need to reference the login).

See *Input Bitfields Per Message* (p. 171) and *Return Bitfields Per Message* (p. 179) in the spec.

---

## 5. Message Type Codes

### 5.1 Session Messages

| Message Type | Code | Direction | Description |
|---|---|---|---|
| Login Request | `0x37` | Member → Cboe | First message on connect |
| Logout Request | `0x02` | Member → Cboe | Graceful session close |
| Client Heartbeat | `0x03` | Member → Cboe | Keep-alive |
| Login Response | `0x24` | Cboe → Member | Login accept/reject |
| Logout | `0x08` | Cboe → Member | Session termination |
| Server Heartbeat | `0x09` | Cboe → Member | Keep-alive |
| Replay Complete | `0x13` | Cboe → Member | End of missed-message replay |

### 5.2 Application Messages — Member to Cboe

| Message Type | Code | Description |
|---|---|---|
| New Order | `0x38` | Submit a new order |
| New Order (Short) | `0x39` | Abbreviated new order |
| New Order Cross | `0x3A` | Cross order (C1/EDGX) |
| New Complex Instrument | `0x3C` | Define complex instrument (C1/C2/EDGX) |
| New Complex Order | `0x3D` | Multi-leg order (C1/EDGX/C2) |
| New Complex Order Short | `0x3E` | Abbreviated complex order |
| New Order Cross Multileg | `0x3F` | Cross multileg (C1/EDGX) |
| Cancel Order | `0x45` | Cancel a live order |
| Mass Cancel Order | `0x46` | Cancel multiple orders |
| Modify Order | `0x4A` | Modify a live order |
| Quote Update | `0x59` | Quote update |
| Purge Orders | `0x62` | Purge orders by criteria |
| Reset Risk | `0x63` | Risk reset |

### 5.3 Application Messages — Cboe to Member

| Message Type | Code | Description |
|---|---|---|
| Order Acknowledgment | `0x25` | Order accepted and working |
| Order Rejected | `0x26` | Order rejected with reason |
| Order Modified | `0x27` | Modification confirmed |
| Order Restated | `0x28` | Order restated (e.g., done-for-day) |
| User Modify Rejected | `0x29` | Modification rejected |
| Order Cancelled | `0x2A` | Cancellation confirmed |
| Cancel Rejected | `0x2B` | Cancellation rejected |
| Order Execution | `0x2C` | Trade fill |
| Trade Cancel or Correct | `0x2D` | Trade bust/correction |
| Mass Cancel Acknowledgment | `0x99` | Mass cancel confirmed |
| Purge Notification | `0x9B` | Purge confirmed |

---

## 6. Session Protocol

### 6.1 Login and Sequencing

- Session messages (Login, Logout, Heartbeat) are **unsequenced** — SequenceNumber = 0 in both directions.
- **Member → Cboe** application messages are **sequenced** using a single sequence stream across all matching units.
- **Cboe → Member** application messages are sequenced **per matching unit** (distinct counter per unit).
- A Login Request must be the **first message** sent after TCP connect.
- Session identified by (username + SessionSubID); **only one concurrent connection** per pair.
- On reconnect, Member sends last received sequence number per unit; Cboe replays missed messages.
- Gaps forward (sequence ahead) are ignored. Gaps backward → Cboe sends `Logout`.
- `Replay Complete` is sent immediately after `Login Response` if no messages need replaying.
- **Cboe rejects all orders during replay.**

### 6.2 Sequence Reset

No formal reset command. To reset: send `Login Request` with `NoUnspecifiedUnitReplay = 0x01` and `NumberOfUnits = 0`. Use `LastReceivedSequenceNumber` from the `Login Response` as the new starting sequence.

### 6.3 Heartbeats

- Trigger: if no data has been sent in either direction for **1 second**.
- Timeout: if Cboe receives no inbound data or heartbeats for **5 seconds** → sends `Logout` and closes.
- Heartbeats from Cboe do **not** increment the sequence number.
- Members are encouraged to have a **1-second heartbeat interval** and similar staleness logic.

### 6.4 Logging Out

1. Member sends `Logout Request`.
2. Cboe drains any queued outbound data.
3. Cboe replies with `Logout` and closes the connection.
4. After receiving a Logout Request, Cboe ignores all inbound messages except `Client Heartbeat`.
5. A member may close TCP without logging out, but may lose queued messages.

### 6.5 Exchange Shutdown

Cboe sends `Logout` (reason = `E` = End of Day) to all connected ports at approximately **17:30 ET** daily without waiting for a logout request.

---

## 7. Session Message Field Tables

### 7.1 Login Request (`0x37`) — Member to Cboe

**Table 11 — Login Request Message Fields**

```
Offset  Len  Type          Field                 Notes
------  ---  ------------  --------------------  ----------------------------------
0       2    Binary        StartOfMessage        0xBA 0xBA
2       2    Binary        MessageLength         Incl. this field, excl. SOM
4       1    Binary        MessageType           0x37
5       1    Binary        MatchingUnit          Always 0 inbound
6       4    Binary        SequenceNumber        Always 0 for session messages
10      4    Alphanumeric  SessionSubID          Session Sub ID supplied by Cboe
14      4    Alphanumeric  Username              Username supplied by Cboe
18      10   Alphanumeric  Password              Password supplied by Cboe
28      1    Binary        NumberOfParamGroups   Number of parameter groups (n ≥ 0)
         …   —             ParamGroup₁..ₙ        See §7.1.1 and §7.1.2
```

**Wire size:** Variable (minimum 29 total bytes with 0 param groups)  
**MessageLength** = 27 + param groups byte count  

**Login Request Example (Table 14):**
```
BA BA           StartOfMessage
3D 00           MessageLength = 61 bytes
37              MessageType = Login Request
00              MatchingUnit = 0
00 00 00 00     SequenceNumber = 0
30 30 30 31     SessionSubID = "0001"
54 45 53 54     Username = "TEST"
54 45 53 54 49 4E 47 00 00 00   Password = "TESTING"
03              NumberOfParamGroups = 3
…               (param groups)
```

#### 7.1.1 Unit Sequences Parameter Group (`0x80`)

Carries the last consumed outbound sequence number per matching unit, so Cboe can replay any missed messages.

```
Offset  Len  Field               Notes
------  ---  ------------------  -----------------------------------------
0       2    ParamGroupLength    Bytes for this group, including this field
2       1    ParamGroupType      0x80
3       1    NoUnspecifiedUnitReplay  0x00 = replay unspecified; 0x01 = suppress
4       1    NumberOfUnits       Unit/sequence pairs to follow
—       1    UnitNumber₁         Unit number
—       4    UnitSequence₁       Last received sequence for unit
…
```

#### 7.1.2 Return Bitfields Parameter Group (`0x81`)

Declares which optional fields Cboe should include in each outbound message type for the remainder of the session.

```
Offset  Len  Field                  Notes
------  ---  ---------------------  -------------------------------------------
0       2    ParamGroupLength       Bytes for this group, including this field
2       1    ParamGroupType         0x81
3       1    MessageType            Return message type (e.g., 0x25 = Order Ack)
4       1    NumberOfReturnBitfields  Bitfield count
5       1    ReturnBitfield₁..ₙ    One byte per bitfield
```

Multiple instances allowed (one per Cboe-to-Member message type you want configured).

---

### 7.2 Logout Request (`0x02`) — Member to Cboe

**Table 15 — Logout Request Message Fields**

```
Offset  Len  Type    Field            Notes
------  ---  ------  ---------------  --------------------------
0       2    Binary  StartOfMessage   0xBA 0xBA
2       2    Binary  MessageLength    8 bytes
4       1    Binary  MessageType      0x02
5       1    Binary  MatchingUnit     Always 0
6       4    Binary  SequenceNumber   Always 0
```

**Total: 10 bytes.** MessageLength = 8. SequenceNumber = always 0 for session messages.

---

### 7.3 Client Heartbeat (`0x03`) — Member to Cboe

**Table 17 — Client Heartbeat Message Fields**

```
Offset  Len  Type    Field            Notes
------  ---  ------  ---------------  --------------------------
0       2    Binary  StartOfMessage   0xBA 0xBA
2       2    Binary  MessageLength    8 bytes
4       1    Binary  MessageType      0x03
5       1    Binary  MatchingUnit     Always 0
6       4    Binary  SequenceNumber   Always 0
```

**Total: 10 bytes.** Identical layout to Logout Request, different MessageType.

---

### 7.4 Login Response (`0x24`) — Cboe to Member

**Table 19 — Login Response Message Fields**

```
Offset  Len   Type          Field                    Notes
------  ----  ------------  -----------------------  ----------------------------------
0       2     Binary        StartOfMessage           0xBA 0xBA
2       2     Binary        MessageLength            Variable (≥ 136 on success)
4       1     Binary        MessageType              0x24
5       1     Binary        MatchingUnit             Always 0
6       4     Binary        SequenceNumber           Always 0
10      1     Alphanumeric  LoginResponseStatus      See codes below
11      60    Text          LoginResponseText        Human-readable description (NUL padded)
71      1     Binary        NoUnspecifiedUnitReplay  Echo of Login Request flag
72      4     Binary        LastReceivedSequenceNumber  Last inbound seq processed by Cboe
76      1     Binary        NumberOfUnits            Unit/sequence pairs to follow
—       1     Binary        UnitNumber₁              A unit number
—       4     Binary        UnitSequence₁            Highest available Cboe→Member seq
…
—       1     Binary        NumberOfParamGroups      Echo of Login Request param groups
—       …     —             ParamGroup₁..ₙ           Echo of Login Request param groups
```

**LoginResponseStatus values:**

| Code | Meaning |
|------|---------|
| `A` | Login Accepted |
| `N` | Not authorized (invalid username/password) |
| `D` | Session is disabled |
| `B` | Session in use (already connected) |
| `S` | Invalid session |
| `Q` | Sequence ahead in Login message |
| `I` | Invalid unit given in Login message |
| `F` | Invalid return bit field in login message |
| `M` | Invalid Login Request message structure |

> **Note:** Response length is variable. Cboe always returns sequence numbers for all units (even those not in the Login Request). **Prepare to handle variable-length Login Response messages.**

---

### 7.5 Logout (`0x08`) — Cboe to Member

**Table 21 — Logout Message Fields**

```
Offset  Len  Type          Field                     Notes
------  ---  ------------  ------------------------  ----------------------------------
0       2    Binary        StartOfMessage            0xBA 0xBA
2       2    Binary        MessageLength             Variable
4       1    Binary        MessageType               0x08
5       1    Binary        MatchingUnit              Always 0
6       4    Binary        SequenceNumber            Always 0
10      1    Alphanumeric  LogoutReason              See codes below
11      60   Text          LogoutReasonText          Human-readable description
71      4    Binary        LastReceivedSequenceNumber  Last inbound seq processed by Cboe
75      1    Binary        NumberOfUnits             Unit/sequence pairs to follow
—       1    Binary        UnitNumber₁
—       4    Binary        UnitSequence₁             Highest available sequence for unit
…
```

**LogoutReason values:**

| Code | Meaning |
|------|---------|
| `U` | User Requested |
| `E` | End of Day |
| `A` | Administrative |
| `!` | Protocol Violation |

---

### 7.6 Server Heartbeat (`0x09`) — Cboe to Member

**Table 23 — Server Heartbeat Message Fields**

```
Offset  Len  Type    Field            Notes
------  ---  ------  ---------------  --------------------------
0       2    Binary  StartOfMessage   0xBA 0xBA
2       2    Binary  MessageLength    8 bytes
4       1    Binary  MessageType      0x09
5       1    Binary  MatchingUnit     Always 0
6       4    Binary  SequenceNumber   Always 0
```

**Total: 10 bytes.** Does **not** increment the outbound sequence number.

---

### 7.7 Replay Complete (`0x13`) — Cboe to Member

**Table 25 — Replay Complete Message Fields**

```
Offset  Len  Type    Field            Notes
------  ---  ------  ---------------  --------------------------
0       2    Binary  StartOfMessage   0xBA 0xBA
2       2    Binary  MessageLength    8 bytes
4       1    Binary  MessageType      0x13
5       1    Binary  MatchingUnit     Always 0
6       4    Binary  SequenceNumber   Always 0
```

Sent immediately after Login Response when there are no messages to replay, or after all replayed messages when there are.

---

## 8. Application Messages — Member to Cboe

### 8.1 New Order (`0x38`)

**Table 27 — New Order Message Fields**

```
Offset  Len   Type          Field                       Notes
------  ----  ------------  --------------------------  ----------------------------------
0       2     Binary        StartOfMessage              0xBA 0xBA
2       2     Binary        MessageLength               Variable
4       1     Binary        MessageType                 0x38
5       1     Binary        MatchingUnit                Always 0
6       4     Binary        SequenceNumber              Incremental application sequence
10      20    Text          ClOrdID                     ASCII 33–126 except , ; | @ "
                                                         NUL-padded. Unique among live orders.
30      1     Alphanumeric  Side                        1 = Buy ('1' = 0x31)
                                                         2 = Sell ('2' = 0x32)
31      4     Binary        OrderQty                    Max 999,999 contracts
35      1     Binary        NumberOfNewOrderBitfields   Count of bitfield bytes following
36      1     Binary        NewOrderBitfield₁           Identifies optional fields present
…       1     Binary        NewOrderBitfieldₙ           Last bitfield
—       …     —             Optional fields…            Per enabled bits, lowest bit first
```

**Required optional fields** (must always be present via bitfield):

| Field | Type | Size | Notes |
|-------|------|------|-------|
| Symbol | Alphanumeric | 8 | Required (some form of symbology) |
| Price | Binary Price | 8 | Required for limit orders. Non-negative. |
| OrdType | Alphanumeric | 1 | Required for market/stop orders. Default = Limit. |
| Capacity | Alphanumeric | 1 | Always required |

**New Order Example (Table 28):**
```
BA BA             StartOfMessage
59 00             MessageLength = 89 bytes
38                MessageType = New Order
00                MatchingUnit = 0
64 00 00 00       SequenceNumber = 100
41 42 43 31 32 33 00…(pad)  ClOrdID = "ABC123"
31                Side = Buy (ASCII '1')
64 00 00 00       OrderQty = 100
04                NumberOfBitfields = 4
04                Bitfield1: Price
C1                Bitfield2: Symbol, Capacity, RoutingInst
01                Bitfield3: Account
17                Bitfield4: MaturityDate, StrikePrice, PutOrCall, OpenClose
70 17 00 00 00 00 00 00   Price = 0.60 (6000/10000)
4D 53 46 54 00…   Symbol = "MSFT"
43                Capacity = 'C' (Customer)
52 00 00 00       RoutingInst = 'R' (Routable)
44 45 46 47 00…   Account = "DEFG"
EF DB 32 01       MaturityDate = 2011-03-19
98 AB 02 00 00 00 00 00   StrikePrice = 17.50
31                PutOrCall = '1' (Call)
4F                OpenClose = 'O' (Open)
```

---

## 9. Field Value Reference

### 9.1 Side (New Order, offset 30)

| Wire Value | ASCII | Meaning |
|-----------|-------|---------|
| `0x31` | `'1'` | Buy |
| `0x32` | `'2'` | Sell |

### 9.2 OrdType

| Wire Value | ASCII | Meaning |
|-----------|-------|---------|
| `0x31` | `'1'` | Market |
| `0x32` | `'2'` | Limit |
| `0x33` | `'3'` | Stop |
| `0x34` | `'4'` | Stop Limit |

### 9.3 Capacity

| Wire Value | ASCII | Meaning |
|-----------|-------|---------|
| `0x41` | `'A'` | Agency |
| `0x43` | `'C'` | Customer |
| `0x4D` | `'M'` | Market Maker |
| `0x50` | `'P'` | Principal |
| `0x46` | `'F'` | Firm |
| `0x4A` | `'J'` | Joint Back Office |

### 9.4 OpenClose

| Wire Value | ASCII | Meaning |
|-----------|-------|---------|
| `0x4F` | `'O'` | Open |
| `0x43` | `'C'` | Close |
| `0x00` | — | None / default |

### 9.5 PutOrCall

| Wire Value | ASCII | Meaning |
|-----------|-------|---------|
| `0x31` | `'1'` | Call |
| `0x30` | `'0'` | Put |

### 9.6 LoginResponseStatus

See §7.4 table above.

### 9.7 LogoutReason

See §7.5 table above.

---

## 10. Sequencing Rules Summary

| Direction | Message Category | Sequencing |
|-----------|-----------------|-----------|
| Member → Cboe | Session (Login, Logout, Heartbeat) | **Unsequenced** (SequenceNumber = 0) |
| Member → Cboe | Application (orders, cancels) | **Single stream** across all matching units |
| Cboe → Member | Session | **Unsequenced** (SequenceNumber = 0) |
| Cboe → Member | Application | **Per matching unit** (independent counter per unit) |

- Cboe recommends (but does not require) Members send sequence numbers on inbound.
- A gap forward in Member's sequence is **ignored** by Cboe.
- A gap backward (duplicate or reused sequence) → Cboe sends `Logout`.

---

## 11. MessageLength Calculation Examples

### Session messages (Logout Request, Client Heartbeat, Server Heartbeat, Replay Complete)
```
Payload = MessageType(1) + MatchingUnit(1) + SequenceNumber(4) = 6 bytes
MessageLength = 6 + 2 = 8
Total wire size = 2 (StartOfMessage) + 8 = 10 bytes
```

### Login Request (minimal — no param groups)
```
Payload = Type(1) + MU(1) + Seq(4) + SessionSubID(4) + Username(4) + Password(10) + NumParamGroups(1)
        = 25 bytes
MessageLength = 25 + 2 = 27
Total = 2 + 27 = 29 bytes
```

---

## 12. Hours of Operation (Eastern Time)

| Exchange | Order Acceptance Start | GTH | RTH |
|----------|----------------------|-----|-----|
| C1 | 8:00 pm (prev day) SPX/VIX/XSP; 7:30 am all products | 8:15–9:25 am (SPX/VIX/XSP) | 9:30 am – 4:00 pm (4:15 pm ETFs/ETNs) |
| C2 | 7:30 am | N/A | 9:30 am – 4:00 pm (4:15 pm) |
| EDGX | 7:30 am | N/A | 9:30 am – 4:00 pm (4:15 pm) |

- C1 also supports a **Curb session**: 4:30–5:00 pm (SPX/VIX/XSP).
- Exchange shuts down ~17:30 ET daily (sends `Logout` with reason `E`).
- Orders remaining after Regular Trading Session that are not eligible for Extended Trading are **automatically cancelled**.

---

## 13. Protocol Features — Key Notes

### Messages in Flight
- Max messages in flight between order handler and matching engine: **128**.
- If unacknowledged messages exceed **1,024**, BOE order handler stops reading from the member TCP socket (flow control).
- Reading resumes when unacknowledged messages fall below **960**.

### GTC/GTD Order Persistence
- GTC and GTD orders persist between sessions. On EDGX/C2, GTC/GTD cancellation deadline is 4:45 pm ET; on C1, 5:15 pm ET.

### Market Order NBBO Width Protection
- Market orders rejected if NBBO width > 100% of midpoint (min $5.00, max $10.00).

### Stale NBBO
- If Cboe detects a stale NBBO, new orders are rejected for the affected class(es). Existing orders remain on the book but cannot be updated.

---

## 14. Common Pitfalls

| Correct | Incorrect |
|---------|-----------|
| StartOfMessage = `0xBA 0xBA` | Using `0xB0 0xE3` or other values |
| MessageLength = payload + 2 | MessageLength = payload only |
| StartOfMessage excluded from MessageLength | Including StartOfMessage in length |
| String fields NUL-padded (0x00) | Space-padded (0x20) |
| Side wire value = ASCII `'1'`/`'2'` (0x31/0x32) | Side wire value = binary 1/2 |
| Session messages: SequenceNumber = 0 | Incrementing sequence on session messages |
| Inbound MatchingUnit = always 0 | Setting MatchingUnit on outbound orders |
| Atomic sequence counter | Hardcoded or non-thread-safe counter |
| `Login Request` must be first message | Sending orders before login |

---

## 15. Simulator Delta (Spec vs. Current Implementation)

Discrepancies between this spec and the current TitaniumBOE-Sim implementation to be addressed in Phase 2+:

| Area | Spec (v2.11.90) | Simulator (current) | Phase |
|------|----------------|---------------------|-------|
| Side wire values | `'1'` = Buy, `'2'` = Sell (ASCII) | `fromByte(1/2)` legacy path covers this | Review |
| Capacity `'C'` | Customer (`0x43`) is a valid value | Not in `Capacity` enum | Phase 2 |
| OrdType wire values | `'1'`=Market, `'2'`=Limit (ASCII) | `fromByte(1/2)` legacy path covers this | Review |
| MessageType codes (session) | `0x37`/`0x24`/`0x09`/`0x13` etc. | Enum has `0x0001`/`0x01F5`/`0x01F8` (website values) | Phase 2 |
| MessageType codes (app) | `0x38`=NewOrder, `0x45`=Cancel | Enum has `0x07D1`/`0x07DA` (website values) | Phase 2 |
| String padding | NUL (0x00) | Space (0x20) in `OrderAcknowledgmentMessage` | Phase 2 |
| `OrderAcknowledgmentMessage` | MessageType = `0x25` (1 byte) | Uses `0x25` but encodes as 1-byte ✅ | OK |

---

## 16. References

- **Spec PDF:** `docs/US_Options_BOE_Specification.pdf` (v2.11.90, Oct 3, 2025)
- **Relevant sections:**
  - §10 "Data Types" (p. 10) — complete type definitions
  - §11 "Optional Fields and Bit Fields" (p. 11)
  - §43 "Session — Message Header Fields" (p. 43) — header layout
  - §44 "Login, Replay and Sequencing" (p. 44)
  - §46 "Heartbeats" (p. 46)
  - §48 "Session Messages — Member to Cboe" (p. 48)
  - §53 "Session Messages — Cboe to Member" (p. 53)
  - §60 "Application Messages — New Order" (p. 60)
  - §196 "List of Optional Fields" (p. 196) — complete optional field directory
  - §216 "List of Message Types" (p. 216) — complete code table
