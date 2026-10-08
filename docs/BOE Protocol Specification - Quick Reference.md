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

Source: spec v2.11.90, Table 134 (p.216).

| Message Type | Code | Sequenced | Description |
|---|---|---|---|
| New Order | `0x38` | Yes | Submit a new order |
| Cancel Order | `0x39` | Yes | Cancel a live order (a blank OrigClOrdID with MassCancelInst makes it a mass cancel) |
| Modify Order | `0x3A` | Yes | Modify a live order |
| New Order Cross | `0x41` | Yes | Cross order |
| Purge Orders | `0x47` | Yes | Purge orders by criteria |
| New Complex Order | `0x4B` | Yes | Multi-leg order |
| New Complex Instrument | `0x4C` | Yes | Define complex instrument |
| Quote Update | `0x55` | Yes | Quote update |
| Reset Risk | `0x56` | Yes | Risk reset |
| Quote Update (Short) | `0x59` | Yes | Abbreviated quote update |
| New Order Cross Multileg | `0x5A` | Yes | Cross multileg |

### 5.3 Application Messages — Cboe to Member

Source: spec v2.11.90, Table 135 (p.216). Unsequenced application messages are sent with
MatchingUnit = 0 and SequenceNumber = 0 and are **not** included in replay.

| Message Type | Code | Sequenced | Description |
|---|---|---|---|
| Order Acknowledgment | `0x25` | Yes | Order accepted and working |
| Order Rejected | `0x26` | **No** | Order rejected with reason |
| Order Modified | `0x27` | Yes | Modification confirmed |
| Order Restated | `0x28` | Yes | Order restated |
| User Modify Rejected | `0x29` | **No** | Modification rejected |
| Order Cancelled | `0x2A` | Yes | Cancellation confirmed |
| Cancel Rejected | `0x2B` | **No** | Cancellation rejected |
| Order Execution | `0x2C` | Yes | Trade fill |
| Trade Cancel or Correct | `0x2D` | Yes | Trade bust/correction |
| Mass Cancel Acknowledgment | `0x36` | **No** | Mass cancel confirmed |
| Cross Order Acknowledgment | `0x43` | Yes | Cross order accepted |
| Cross Order Rejected | `0x44` | **No** | Cross order rejected |
| Cross Order Cancelled | `0x46` | Yes | Cross order cancelled |
| Purge Rejected | `0x48` | **No** | Purge rejected |
| Complex Instrument Accepted | `0x4D` | Yes | Complex instrument accepted |
| Complex Instrument Rejected | `0x4E` | **No** | Complex instrument rejected |
| Quote Update Acknowledgment | `0x51` | Yes | Quote accepted |
| Quote Restated | `0x52` | Yes | Quote restated |
| Quote Cancelled | `0x53` | Yes | Quote cancelled |
| Quote Execution | `0x54` | Yes | Quote fill |
| Risk Reset Acknowledgment | `0x57` | **No** | Risk reset confirmed |
| Quote Update Rejected | `0x58` | **No** | Quote rejected |

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

Remaining differences between this spec and the current TitaniumBOE-Sim implementation:

| Area | Spec (v2.11.90) | Simulator (current) | Status |
|------|----------------|---------------------|--------|
| Source IP filtering | unknown source IP ranges are blocked | any IP is accepted | Won't fix — out of scope for a simulator; network access control belongs to the deployment |
| Session message codes | `0x37`/`0x24`/`0x09`/`0x13` | matches the spec | ✅ Fixed |
| New Order code | `0x38` | `0x38` | ✅ Fixed |
| `Side` wire values | `'1'` = Buy, `'2'` = Sell (ASCII) | `'1'`/`'2'`; `fromByte` also accepts legacy `1`/`2` and `'B'`/`'S'` for records already in RocksDB | ✅ Fixed |
| `OrdType` wire values | `'1'` = Market, `'2'` = Limit (ASCII) | `'1'`/`'2'` | ✅ Fixed |
| `Capacity` `'C'` | Customer (`0x43`) is a valid value | present in the `Capacity` enum | ✅ Fixed |
| `OrderAcknowledgmentMessage` | MessageType = `0x25` (1 byte) | `0x25`, encoded as 1 byte | ✅ Fixed |
| String padding | NUL (`0x00`) for Alpha, Alphanumeric and Text | NUL in every encoder; inbound text fields must be NUL-padded (a space-padded Alpha field is rejected, see *Character sets*) | ✅ Fixed |
| Character sets | Alpha = A-Z, a-z; Alphanumeric = plus 0-9; Text = printable ASCII; ClOrdID = ASCII 33-126 except `,` `;` `\|` `@` `"` | checked byte by byte on every text field the simulator reads, and only NUL may follow the first NUL. Login (SessionSubID, Username, Password) → Login Response `M`; New Order / Cancel / Modify → Order Rejected / Cancel Rejected / User Modify Rejected `Z` "Invalid character 0x.. in Field (...)". Demo and REST-registered passwords are alphanumeric so they can log in over BOE. Previously any byte was accepted | ✅ Fixed |
| `DateTime` | nanoseconds past the UNIX epoch (UTC) | `BoeTime.nowEpochNanos()` (previously `System.nanoTime()`, which has an arbitrary origin) | ✅ Fixed |
| `Date` (MaturityDate) | YYYYMMDD as a 4-byte integer | `BoeTime.toYyyymmdd()` in New Order and Order Acknowledgment (New Order previously sent days since 1970) | ✅ Fixed |
| Load handling | never drop member messages; stop reading the socket above 1,024 unacknowledged, resume below 960 | per-connection reader + single processor; the reader pauses above 1,024 and resumes below 960 (same values as the spec). A 1,000 msg/s token bucket slows the processor; session messages are not counted (previously excess messages were silently dropped at 100/min) | ✅ Fixed |
| Cancel / Modify message codes | `0x39` = Cancel, `0x3A` = Modify (Table 134) | `0x39` / `0x3A` — matches. An earlier version of this document listed `0x45` / `0x4A` by mistake | ✅ Matches |
| Session messages unsequenced | Login Response, Logout, Server Heartbeat, Replay Complete: MatchingUnit = 0, SequenceNumber = 0 | all four sent with 0 / 0 (previously they consumed outbound sequence numbers and echoed the client's MatchingUnit) | ✅ Fixed |
| Unsequenced application messages | Order Rejected, User Modify Rejected, Cancel Rejected, Mass Cancel Acknowledgment: SequenceNumber = 0, not replayed | sent with 0 / 0 and kept out of the replay journal | ✅ Fixed |
| Outbound MatchingUnit | the unit that created the message; 0 only for session traffic | sequenced application messages use unit **1** (the simulator's single matching engine); the client's inbound MatchingUnit is ignored | ✅ Fixed |
| Inbound sequencing | gap forward ignored; backward or repeated → Logout and drop; 0 = unsequenced | `BoeSessionState.checkInbound`: Logout with reason `!` and the connection is closed | ✅ Fixed |
| Sequence state per session | outbound sequence and last processed inbound belong to username + SessionSubID, not to the TCP connection | `BoeSessionRegistry` keeps them across reconnects (in memory; cleared by the daily reset and on server restart) | ✅ Fixed |
| Replay | Unit Sequences group (`0x80`) in Login Request; replay missed sequenced messages, then Replay Complete; orders received during replay rejected (`y`) | implemented, including executions that happened while the member was disconnected | ✅ Fixed |
| Login Response format | unit/sequence pair for every unit, binary NoUnspecifiedUnitReplay, echoed parameter groups | implemented; Logout also carries the unit pairs | ✅ Fixed |
| Concurrent sessions per user | one connection per username **+ SessionSubID** | one connection per **username** (stricter: executions are routed by username) | Open — deliberate |
| Heartbeat timing | Server Heartbeat after 1 s with nothing sent; Logout after 5 s with nothing received | 1 s / 5 s by default (`heartbeatIntervalSeconds` / `heartbeatTimeoutSeconds`). Previously 10 s / 30 s and sent at a fixed rate even with traffic flowing | ✅ Fixed |
| Heartbeat timeout | any inbound data counts; timeout ends with a Logout | any inbound message resets the timer, which starts at login; on expiry a Logout (`!`, "Heartbeat timeout") is sent before closing. Previously only Client Heartbeats counted, the timer never started if the client sent none, and the connection was dropped without a Logout | ✅ Fixed |
| Login Request structure | invalid structure → Login Response `M` | length, parameter-group lengths, one `0x80` group at most, one `0x81` group per message type, NoUnspecifiedUnitReplay `0x00`/`0x01`, no repeated unit and no trailing bytes are checked; the echoed NumberOfParamGroups is the received one, so unknown groups are echoed consistently; failure → `M` with the reason, then the connection is closed. Previously a malformed login got no response at all | ✅ Fixed |
| Return bitfields at login | invalid return bitfields → Login Response `F` naming the byte and bit | checked against the *Return Bitfields Per Message* tables (`ReturnBitfieldRules`). Note: the spec's own Table 14/20 examples request Order Execution byte 5 bit 64 and byte 7 bit 1, which those tables mark "-"; the examples are "for illustrative purposes only" and the simulator follows the tables | ✅ Fixed |
| Login Request first | the Login Request must be the first message | anything else before a successful login, or a second Login Request on the same connection → Logout `!` and close. Previously orders were rejected and the connection stayed open | ✅ Fixed |
| Inbound MatchingUnit | always 0 for Member → Cboe messages (the spec does not say what to do otherwise) | Login Request with MatchingUnit or SequenceNumber ≠ 0 → `M`; New Order / Modify / Cancel with MatchingUnit ≠ 0 → Order Rejected / User Modify Rejected / Cancel Rejected with reason `Z` and the text "MatchingUnit must be 0 for inbound messages", plus a log warning; Client Heartbeat / Logout Request with a non-zero header → log warning only. Purge Orders and Reset Risk target a unit through a body field, not the header | ✅ Fixed |
| End of day | Logout `E` to every connected port when the exchange shuts down, scheduled 17:30 ET | at 17:30 America/New_York (DST-aware) every connected session gets Logout `E` "End of day" before the daily reset clears orders, trades and session state. Previously the reset ran at midnight UTC with sessions still connected and no Logout | ✅ Fixed |
| Server shutdown | (not covered by the spec) | every connected session gets Logout `A` "Server shutting down" before the connection is closed; previously connections were dropped silently | ✅ Simulator choice |
| Logout example (Table 22) | — | the example has two typos: MessageLength `55 00` (85) does not match the Table 21 offsets (84 for two units), and LastReceived `54 5A 02 00` is 154,196, not the 150,100 in its note (`54 4A 02 00`, as in Table 20). The simulator follows Table 21 | ℹ️ Spec errata |
| New Order Bitfield 1 | bit 8 = ExecInst, bit 16 = OrdType, bit 32 = TimeInForce (Table 132) | matches. Previously OrdType was read from bit 8 and TimeInForce from bit 16, so a spec client's Market order was booked as a Limit order | ✅ Fixed |
| New Order optional fields | every field the table allows may be sent; blank fields are rejected | every field of Table 132 is known with its length (List of Optional Fields). Supported fields are read; informational ones (EchoText, CMTANumber, RoutStrategy, ClientIDAttr…) are consumed and ignored; fields that would change execution and are not implemented (MinQty, ExpireTime, StopPx, PreventMatch, DisplayRange, AuctionId, TargetPartyID, FloorDestination; ExecInst/MaxFloor/DisplayIndicator/PriceType/FloorRoutingInst unless default) → Order Rejected `Z` naming the field; blank or reserved bits → rejected. Previously unsupported fields were not consumed and every following field was misread | ✅ Fixed |
| OrdType / Price | Price required for Limit, rejected on Market; Stop requires StopPx | Market with Price → rejected; Stop / Stop Limit → rejected as unsupported; an unknown OrdType is rejected instead of leaving the order unanswered | ✅ Fixed |
| Cancel Order fields | only the Table 36 bitfield fields; SendTime required | ClearingFirm, RiskRoot, MassCancelID, RoutingFirmID, MassCancelInst and SendTime are read; any other bit, a missing SendTime or a message shorter than its fields → Cancel Rejected `Z`. Previously SendTime was optional and blank bits (MassCancelLockout, MassCancel, Symbol, OperatorId…) were accepted | ✅ Fixed |
| Cancel rejected | Cancel Rejected with the reason | unknown, terminated or another user's order → `O`; no longer cancellable → `J`. Previously a rejected cancel got no response at all | ✅ Fixed |
| Mass cancel filters | Clearing Firm Filter `A`/`F`, RiskRoot always applied, Instrument Type Filter, Lockout | `A`/`F` and RiskRoot applied together; `C` (complex only) cancels nothing because the simulator has no complex orders; Lockout `L` → Cancel Rejected `Z` "not supported"; invalid MassCancelInst values → `Z`. Previously the Table 38 example (`FSLB`, RiskRoot MSFT) cancelled every order of firm TEST and ignored the lockout | ✅ Fixed |
| Mass cancel acknowledgment | Acknowledgement Style `M` = one Order Cancelled per order, `S` = one Mass Cancel Acknowledgment (`0x36`), `B` = both; MassCancelID required for `S`/`B`, blank for `M` | implemented; Mass Cancel Acknowledgment is unsequenced. Previously the client received nothing | ✅ Fixed |
| Identical mass cancels | more than 10 identical mass cancels per second per port are rejected | per connection, 1 s sliding window; identical = same RiskRoot, ClearingFirm and Lockout / Instrument Type / GTC filters → Cancel Rejected `K` | ✅ Fixed |
| Cancel Order examples (Tables 37, 38) | — | MessageLength `2A 00` (42) and `54 00` (84) are one byte short: the fields listed add up to 43 and 85. A client sending them verbatim desynchronises the stream; the simulator frames by MessageLength, as the spec says | ℹ️ Spec errata |
| Mass Cancel Acknowledgment example (Table 111) | — | MessageLength `29 00` (41) does not match the Table 110 offsets, which add up to 42; the simulator follows Table 110 | ℹ️ Spec errata |
| Carried GTC/GTD orders | GTC/GTD orders are carried to the next session and can be cancelled there | not applicable: GTC and GTD are rejected (see *TimeInForce*), so no order outlives the end-of-day reset | Not applicable |
| Modify Order fields | only the p.176 fields; Side and FrequentTraderID are `-` | ClearingFirm, OrderQty, Price, OrdType, CancelOrigOnReject, RoutingFirmID read; ExecInst / MaxFloor / StopPx → User Modify Rejected `Z` unless default; Side, FrequentTraderID, blank or reserved bits and bitfields ≥ 3 → `Z`. Previously Side was accepted and a reserved bit desynchronised every following field | ✅ Fixed |
| Modify time priority | kept on an OrderQty decrease with no other change; lost on any other change or on no change | the order is reduced in place in its queue; otherwise it goes to the back of the price level. Previously every modify lost priority | ✅ Fixed |
| Modify ClOrdID | new ClOrdID unique; reuse allowed only on a pure OrderQty decrease | another live order's ClOrdID, or reuse with any other change → `D`. Previously a chain A → B → C left B live in the cache, so a Cancel with B cancelled the order | ✅ Fixed |
| CancelOrigOnReject | `Y` = cancel the original order if the modify fails | User Modify Rejected followed by Order Cancelled. Previously read and ignored | ✅ Fixed |
| Modifications per order | 1,295 per order per trading day, then only Cancel | the 1,296th → `Z` | ✅ Fixed |
| Modify reject reasons | — | another user's order → `O` (as for Cancel); unforeseen → `Z` instead of `X` (Order expired); an unknown OrdType no longer escapes as an exception | ✅ Fixed |
| Quote Update / Quote Update (Short) | `0x55` / `0x59`, Bulk Quoting ports and Market Makers only | not implemented: no Bulk Quoting ports; the message is logged and ignored | Not applicable |
| Quote Update examples (Tables 41-45) | — | Tables 41 and 42 are the same table and disagree on how to delete a quote (size 0 vs price 0). The Table 45 (Short) example repeats MessageLength `91 00` (145) from the long format; with two quotes the fields add up to 83 bytes, so it should be `51 00` (81) | ℹ️ Spec errata |
| TimeInForce | Day, GTC, At the Open, IOC, FOK, GTD, At the Close; market orders are implicitly IOC | Day rests; IOC cancels the unfilled part and FOK cancels the whole order unless the crossable liquidity (excluding own orders when self-trade is prevented) covers it — both answered with Order Acknowledgment then Order Cancelled `N` (*Ran out of liquidity*); market orders behave as IOC. GTC, At the Open, GTD and At the Close → Order Rejected `Z` "not supported by the simulator" (no auctions, no multi-day sessions); unknown values → `Z`. Previously every order behaved as Day and an unfilled market order stayed live outside the book | ✅ Fixed |
| Liquidity indicator | BaseLiquidityIndicator `A` = added (resting), `R` = removed (incoming) | `R` for the side of the incoming order (`Trade.aggressorSide`), `A` for the resting one. Previously the buyer was always `R` | ✅ Fixed |
| Max open orders | 200,000 per BOE port; reject reason `o` | **2,000 per BOE session (scaled ÷100** so the limit is reachable on a PC); reject reason `o`. REST and bot orders are not counted | ✅ Fixed (scaled) |
| Outbound sequencing | strictly increasing on the wire | sequence assigned and written under one lock (`sendSequenced`); before, executions and heartbeats from other threads could reorder it. A sequenced message without a logged-in session state throws `IllegalStateException` instead of going out with sequence 0 | ✅ Fixed |

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
