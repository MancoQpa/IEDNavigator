# Análisis de Archivos SCL - IEDNavigator

> Generado: 2026-04-18
> Directorio analizado: `iec61850_java_explorer/SIN SMV/CID/`

---

## 1. Resumen de IEDs analizados

| # | Archivo | IED Name(s) | Fabricante | Tipo/Modelo | LDs | LNs | LN Classes únicas |
|---|---------|-------------|------------|-------------|-----|-----|-------------------|
| 1 | ABB_CID.CID | FP1REF630 | ABB | 630 series (REF630) | 1 | 4 | LLN0, LPHD, RDRE, GGIO |
| 2 | ABB_RED670.cid | AA2D1Q11A2, AA2D1Q11A1, AA2D1Q12A6, AA2D1Q12FP1, AA2D1Q11A3, AA2D1Q12A3, AA2D1Q12FP2, AA2D1Q11FP1 | ABB | IED670/650/RED670/REC670/REC650 | 28 | 492 | 32 clases (ver tabla 3A) |
| 3 | BACEJQ01I08.cid | BACEJQ01I08 | Schneider Electric | ION7400 | 1 | 13 | LLN0, LPHD, GGIO, MHAI, MMTR, MMXU, MSQI, MSTA, RDRE |
| 4 | BACEJQ02I14.cid | BACEJQ02I14 | Schneider Electric | ION7400 | 1 | 13 | LLN0, LPHD, GGIO, MHAI, MMTR, MMXU, MSQI, MSTA, RDRE |
| 5 | ES-ENC.scd | TR1, TR2, TR3, TR4 | NRR | PCS-9611S (x4) | 32 | 762 | 24 clases (ver tabla 3A) |
| 6 | Ingeteam_CD0.cid | — | — | — | 0 | 0 | **VACÍO** |
| 7 | Ingeteam_ZT0.cid | — | — | — | 0 | 0 | **VACÍO** |
| 8 | LTPBO_ESVHA.cid | LTPBO_ESVHA, LTVHA_ESPBO | SIEMENS | 7SL87 (x2) | 26 | 83 | 24 clases (ver tabla 3A) |
| 9 | LTVHA_ESPBO.cid | LTVHA_ESPBO, LTPBO_ESVHA | SIEMENS | 7SL87 (x2) | 26 | 83 | 24 clases (ver tabla 3A) |
| 10 | MICOM_P143_B5AED2.icd | TEMPLATE | SCHNEIDER ELECTRIC | P143 (Feeder+AR+CS) | 5 | 89 | 25 clases (ver tabla 3A) |
| 11 | MICOM_P545_K4AED2.icd | TEMPLATE | SCHNEIDER ELECTRIC | P545 (Diff+Distancia) | 5 | 127 | 31 clases (ver tabla 3A) |
| 12 | MU1.1.cid | MU11 | NRR | PCS-221S (Merging Unit) | 5 | 115 | 9 clases |
| 13 | Project_SEL.scd | SEL487B_cid_1, FP1REF630 | SEL + ABB | SEL_487B + 630 series | 6 | 193 | 10 clases |
| 14 | SEL_ARCH.CID | SEL487B_cid_1 | SEL | SEL_487B (Bus Diff + BF) | 5 | 189 | 10 clases |
| 15 | Siemens_7SL87.cid | B04_R14_F003, B01_R11_F003, B02_R12_F003, B04_W14_D001, B04_W14_D002, B04_W14R_D001 | SIEMENS | 7SL87 + 6MD85 | 59 | 354 | 52 clases (ver tabla 3A) |
| 16 | Siemens_7UT85.cid | B02_R22_F003, B02_W12_D002 | SIEMENS | 7UT85 + 6MD85 | 43 | 235 | 36 clases (ver tabla 3A) |
| 17 | Siprotec_7UM85.icd | TEMPLATE | SIEMENS | 7UM85 (Motor Prot.) | 5 | 129 | 38 clases (ver tabla 3A) |
| 18 | Siprotec_7UM85_CID.icd | TEMPLATE | SIEMENS | 7UM85 (Motor Prot.) | 5 | 129 | 38 clases (ver tabla 3A) |
| 19 | TRAfo_1.cid | IED002 | NRR | PCS-221S (Trafo) | 5 | 116 | 7 clases |
| 20 | ZIV_UC.CID | TBOCFQ05I01 | ZIV | #MCVA_N616M (Control UC) | 1 | 42 | 26 clases |
| 21 | cbo2.cid | cbo2 | Schneider Electric | ION7400 | 1 | 17 | LLN0, LPHD, GGIO, MHAI, MMTR, MMXU, MSQI, MSTA, RDRE |
| 22 | proyecto_final_scd.scd | LTVHA_ESPBO, LTPBO_ESVHA | SIEMENS | 7SL87 (x2) | 48 | 158 | 24 clases |

**Notas:**
- Los archivos `Ingeteam_CD0.cid` e `Ingeteam_ZT0.cid` están completamente vacíos (0 bytes).
- Los archivos `.icd` usan `name="TEMPLATE"` (template ICD, sin nombre de IED real).
- Los archivos `.scd` contienen múltiples IEDs (subestación completa o proyecto).
- ABB_RED670.cid y ES-ENC.scd son archivos SCD (múltiples IEDs), no CID individuales.

---

## 2. Análisis por archivo

### 2.1 ABB_CID.CID

**IED:** `FP1REF630` | ABB REF630 ver1.2.0.10 | Relay de alimentador (Feeder Protection)

#### Logical Devices
| inst | desc |
|------|------|
| LD0 | (único LD) |

#### Logical Nodes
| lnClass | inst | prefix | lnType |
|---------|------|--------|--------|
| LLN0 | "" | — | ABBIED600_LLN0 |
| LPHD | 1 | — | ABBIED600_REV1_LPHD |
| RDRE | 1 | DR | ABBIED600_DRRDRE (Disturbance Report) |
| GGIO | 1 | SP | ABBIED600_REV1_SPGGIO |

#### DOs y DAs relevantes
- **RDRE (DR):** RcdMade(SPS/ST), FltNum(INS/ST), RcdStr(SPS/ST), MemUsed(INS/ST), RcdClr(SPS/ST+EX), MemUsedAlm(SPS/ST+EX)
- **LLN0:** Mod(INC/ST+CO+CF+DC), Beh(INS/ST+DC), Health(INS/ST+DC), NamPlt(LPL/DC+EX)
- **LPHD:** PhyNam(DPL/DC), PhyHealth(INS/ST), Proxy(SPS/ST)
- **GGIO:** Ind(SPS/ST)

#### Enumeraciones encontradas
| EnumType | Valores |
|----------|---------|
| Mod | on(1), blocked(2), test(3), test/blocked(4), off(5) |
| Beh | on(1), blocked(2), test(3), test/blocked(4), off(5) |
| Health | Ok(1), Warning(2), Alarm(3) |
| ctlModel | status-only(0), direct-with-normal-security(1), sbo-with-normal-security(2), direct-with-enhanced-security(3), sbo-with-enhanced-security(4) |
| orCategory | not-supported(0)..process(8) |

#### FCs presentes
ST, CO, CF, DC, EX

#### Comunicación (GOOSE/SMV)
- **GSE:** 1 GOOSE publisher (`gcb1` en LD0), MAC `01-0C-CD-01-00-00`, APPID `0001`, VLAN 000, prio 4

---

### 2.2 ABB_RED670.cid (SCD – 8 IEDs)

**IEDs:** AA2D1Q11A2 (REC670), AA2D1Q11A1 (REC670), AA2D1Q12A6 (REC650), AA2D1Q12FP1 (RED670), AA2D1Q11A3 (REC650), AA2D1Q12A3 (REC650), AA2D1Q12FP2 (RED670), AA2D1Q11FP1 (REC670) — todos ABB IED670/650

#### Logical Devices (muestra, IED principal AA2D1Q11A2)
| inst | desc |
|------|------|
| LD0 | (principal) |
| SES_1 | Synchronizing Station 1 |
| SES_2 | Synchronizing Station 2 |
| SES_3 | Synchronizing Station 3 |
| OV2_1, EF4_1, OC4_1, UV2_1, UV2_2 | Protecciones |

#### LN Classes únicas presentes
CBAY, CILO, CRSV, CSWI, GGIO, LLN0, LPHD, MMTR, MMXN, MMXU, MSQI, PDIF, PDIS, PHAR, PPAM, PSCH, PSOF, PTOC, PTOV, PTRC, PTTR, PTUV, RBRF, RDIR, RDRE, RFLO, RFUF, RPSB, RREC, RSYN, XCBR, XSWI (32 clases)

#### Enumeraciones adicionales vs ABB_CID
| EnumType | Descripción |
|----------|-------------|
| range | normal/high/low/high-high/low-low |
| SwTyp | Load Break/Disconnector/Earthing Switch/HSES |
| SwOpCap | None/Open/Close/Open and Close |
| seqT | pos-neg-zero / dir-quad-zero |
| CBOpCap | None/Open/Close-Open/.../Close-Open-Close-Open |
| dir | unknown/forward/backward/both |
| FltLoop | PhaseAtoGround..Others |
| AutoRecSt | Ready/InProgress/Successful |

#### FCs presentes
ST, MX, CO, CF, DC, EX, SV

#### Comunicación (GOOSE)
- 8 GSE publishers (uno por IED) en LD0, cbName=gcb_A

---

### 2.3 BACEJQ01I08.cid / BACEJQ02I14.cid / cbo2.cid

**IEDs:** BACEJQ01I08, BACEJQ02I14, cbo2 | Schneider Electric ION7400 | Medidor de Energía y Calidad Clase 0.2S

**Estructura idéntica** (BACEJQ01I08 tiene 13 LN, cbo2 tiene 17 LN con GGIOs adicionales)

#### Logical Devices
| inst | desc |
|------|------|
| LD0 | único |

#### Logical Nodes
| lnClass | inst | prefix | Descripción |
|---------|------|--------|-------------|
| LLN0 | "" | — | General |
| LPHD | 1 | — | Physical device |
| GGIO | 1 | ONB1_ | Onboard I/O (status/control) |
| GGIO | 6 | CUS1_ | Custom analogue inputs (16 x AnIn) |
| GGIO | 7 | CUS2_ | Custom digital inputs (16 x Ind) |
| MHAI | 1 | — | Harmonic analysis (armónicos completos) |
| MMTR | 1 | — | Medidor de energía |
| MMXU | 1 | M03_ | Medición trifásica principal |
| MSQI | 1 | — | Componentes simétricas |
| MSTA | 1 | — | Demanda/estadísticas |
| RDRE | 1,2,3 | — | Disturbance records |

#### DOs relevantes (MMXU / M03_)
A, PhV, PPV, W, VAr, VA, PF, TotW, TotVAr, TotVA, TotPF, Hz

#### DOs relevantes (MHAI)
ThdA, ThdPPV, ThdOddA, ThdEvnA, HCfA, HKf (por fase: phsA/B/C/neut/net, phsAB/BC/CA)

#### DOs relevantes (MMTR)
TotVAh, TotWh, TotVArh, SupWh, SupVArh, DmdWh, DmdVArh (FC=ST, INT64 actVal)

#### DOs relevantes (MSTA)
AvVA, MaxVA, MinVA, AvW, MaxW, MinW, AvVAr, MaxVAr, MinVAr (FC=MX)

#### FCs presentes
ST, MX, CO, CF, DC, EX

---

### 2.4 ES-ENC.scd (SCD – 4 IEDs NRR PCS-9611S)

**IEDs:** TR1, TR2, TR3, TR4 | NRR | PCS-9611S (Feeder Protection) v1.00

#### Logical Devices (por IED, aprox.)
Incluye: LD0, PIGO, y LDs funcionales por alimentador

#### LN Classes únicas presentes
CILO, CSWI, GGIO, LGOS, LLN0, LPHD, LTMS, LTRK, MMTR, MMXN, MMXU, MSQI, PTOC, PTRC, RBRF, RDIR, RDRE, RFLO, RREC, RSYN, TCTR, TVTR, XCBR, XSWI (24 clases)

**Destacado:** Incluye **LGOS** (GOOSE Subscription LN), **TCTR/TVTR** (transformadores de medida), **MMXN** (medición no convencional).

#### FCs presentes
ST, MX, CO, CF, DC, EX

#### Comunicación (GOOSE)
- 4 IEDs × 1 GSE publisher cada uno (`gocb0` en LD PIGO)

---

### 2.5 Ingeteam_CD0.cid / Ingeteam_ZT0.cid

**Archivos completamente vacíos (0 bytes).** No contienen datos SCL.

---

### 2.6 LTPBO_ESVHA.cid / LTVHA_ESPBO.cid

**IEDs:** LTPBO_ESVHA + LTVHA_ESPBO | SIEMENS | 7SL87 (Protección de Línea) V08.83.04

Cada archivo CID contiene los **dos IEDs** (el propio + el remoto para suscripción GOOSE).

#### Logical Devices (IED principal)
| inst | desc |
|------|------|
| Application | General |
| BinIO | Binary I/O |
| BinIO_BinaryInputs | Entradas binarias |
| BinIO_BinaryOutputs | Salidas binarias |
| CB1 | Interruptor 1 |
| CB1_Fundamental | Medición fundamental CB1 |
| Ln1 | Línea 1 |
| Ln1_Energy | Energía línea |
| Ln1_FundSymComp | Componentes simétricas |
| Ln1_OperationalValues | Valores operativos |
| Ln1_ProcessMonitor | Monitor de proceso |
| Mod1 (Ethernet), Mod2 (USART), Mod3 (ETH-BB) | Módulos de comunicación |
| PowS | Power Supply |
| PowS_MeasPointI3ph1, PowS_MeasPointV3ph1 | Puntos de medición |
| Rec, Rec_FaultRecorder | Registradores |
| Ln1_21DistanceProt1 | Protección de distancia |
| Ln1_8521PermOverr | Esquema POTT |

#### LN Classes únicas presentes
CALH, CILO, CSWI, GAPC, LCCH, LLN0, LPHD, LTIM, LTMS, LTRK, MMTR, MMXN, MMXU, MSQI, PDIS, PSCH, PTRC, RBRF, RDRE, RFLO, TCTR, TVTR, XCBR, ZLIN (24 clases)

**Destacado:** PDIS (distancia), ZLIN (impedancia de línea), LCCH (canal de comunicación), LTIM/LTMS/LTRK (tiempo/seguimiento), GAPC (protección genérica automática).

#### FCs presentes
ST, MX, CO, CF, DC, EX, SP, SE, SV, BL, OR, SR

**Destaca:** FC=SE (Setting Edit activo – grupos de ajuste), FC=SV (Substitution), FC=BL (Blocking), FC=OR (originador), FC=SR (setting group read).

#### Comunicación (GOOSE)
- 4 GSE publishers: `Ln1_8521PermOverr/Control_DataSet` + `CB1/Control_DataSet` (para cada IED)

---

### 2.7 MICOM_P143_B5AED2.icd

**IED:** TEMPLATE | SCHNEIDER ELECTRIC | P143 (Feeder Protection + Auto-reclose + Check Sync) vB5A

#### Logical Devices
| inst | desc |
|------|------|
| Control | P143 Controls Domain |
| Measurements | P143 Measurements Domain |
| Protection | P143 Protection Domain |
| Records | P143 Records Domain |
| System | P143 System Domain |

#### LN Classes únicas presentes
CILO, GGIO, LLN0, LPHD, LTIM, LTMS, MMTR, MMXU, MSQI, MSTA, PDIF, PFRC, PTOC, PTOF, PTOV, PTRC, PTTR, PTUF, PTUV, RBRF, RDRE, RFLO, RREC, RSYN, XCBR (25 clases)

**Destacado:** PFRC (frecuencia), PTOF/PTUF (sobre/subfrecuencia), PTOV/PTUV (tensión), PTTR (sobretemperatura), PDIF (diferencial), RSYN (sincronismo), RREC (recierre).

#### Enumeraciones adicionales
| EnumType | Descripción |
|----------|-------------|
| AutoRecStKind | Ready/InProgress/Successful/WaitingForTrip/TripFromProtection/FaultDisappeared/WaitToComplete/CBclosed/CycleUnsuccessful/Unsuccessful/Aborted |
| FaultDirectionKind | unknown/forward/backward/both |
| SIUnitKind | Completo (1=none, 5=A, 29=V, 33=Hz, 38=W, 61=VA, 62=Watts, 63=VAr, 72=Wh, 73=VArh...) |
| MultiplierKind | y(-24)..E(18) |
| AddCause | 30 valores de causa adicional |

#### FCs presentes
ST, MX, CO, CF, DC, EX, SP

---

### 2.8 MICOM_P545_K4AED2.icd

**IED:** TEMPLATE | SCHNEIDER ELECTRIC | P545 (Current Differential + Distance) vK4A

#### Logical Devices
| inst | desc |
|------|------|
| Control | Controls Domain |
| Measurements | Measurements Domain |
| Protection | Protection Domain |
| Records | Records Domain |
| System | System Domain |

#### LN Classes únicas presentes
CALH, CILO, GAPC, GGIO, LLN0, LPHD, LTIM, LTMS, MMTR, MMXU, MSQI, MSTA, PDIF, PDIS, PFRC, PSCH, PSOF, PTOC, PTOF, PTOV, PTRC, PTTR, PTUF, PTUV, RBRF, RDRE, RFLO, RPSB, RREC, RSYN, TVTR, XCBR (32 clases)

**Destacado:** PDIS (distancia), RPSB (Power Swing Blocking), PSCH (teleprotección), PSOF (Switchonto-fault), GAPC, CALH (alarmas).

#### GOOSE
- 8+ GSE publishers en LD System (gcb01..gcb08+)

#### FCs presentes
ST, MX, CO, CF, DC, EX, SP

---

### 2.9 MU1.1.cid

**IED:** MU11 | NRR | PCS-221S (Merging Unit) v1.00

#### Logical Devices
| inst | desc |
|------|------|
| LD0 | General + control |
| MU01 | Merging Unit (datos de muestreo) |
| RCD | Registrador |
| MUGO | GOOSE output |
| MUSV | Sampled Values output |

#### LN Classes únicas
GGIO, LCCH, LGOS, LLN0, LPHD, PTRC, RDRE, TCTR, TVTR

**Destacado:** LGOS (GOOSE Subscription), TCTR/TVTR (transformadores), MUSV LD (bloque SMV).

#### FCs presentes
ST, MX, CO, CF, DC, EX, SP, SE, SV, BL, OR

#### Comunicación
- 1 GSE publisher (LD0/gocb0)
- 1 SMV publisher (MUSV/smvcb0)

---

### 2.10 Project_SEL.scd

**IEDs:** SEL487B_cid_1 (SEL) + FP1REF630 (ABB) — SCD de proyecto combinado

#### LN Classes
CSWI, GGIO, LLN0, LPHD, MMXN, PDIF, PTRC, RDRE, XCBR, XSWI (10 clases — principalmente SEL487B)

#### FCs presentes
ST, MX, CO, CF, DC, EX

#### Comunicación (GOOSE)
- SEL487B: `CFG/Goose_SEL`
- ABB REF630: `LD0/gcb1`

---

### 2.11 SEL_ARCH.CID

**IED:** SEL487B_cid_1 | SEL | SEL_487B (Bus Differential + Breaker Failure) ICD-487B-R201-V0

#### Logical Devices
| inst | desc |
|------|------|
| CFG | Data Sets, BRCBs, URCBs |
| PRO | Protection |
| MET | Metering |
| CON | Remote Control |
| ANN | Annunciation |

#### LN Classes
CSWI, GGIO, LLN0, LPHD, MMXN, PDIF, PTRC, RDRE, XCBR, XSWI (10 clases)

**Destacado:** Arquitectura SEL típica con LD separados por función (CFG, PRO, MET, CON, ANN). PDIF = Bus Differential.

#### Enumeraciones
SIUnit (completo, 75+ unidades), multiplier (y..E), orCategory, dir, CBOpCap, SwTyp, SwOpCap, Mod, Beh, Health, ctlModel

#### FCs presentes
ST, MX, CO, CF, DC, EX

#### Comunicación (GOOSE)
- 1 GSE publisher: `CFG/Goose_SEL`

---

### 2.12 Siemens_7SL87.cid (SCD – 5 IEDs)

**IEDs:** B04_R14_F003 (7SL87), B01_R11_F003 (7SL87), B02_R12_F003 (7SL87), B04_W14_D001 (6MD85), B04_W14_D002 (6MD85), B04_W14R_D001 (6MD85)

#### Logical Devices (IED 7SL87 principal - muestra)
Incluye LDs detallados por función: QB1, QB9, Ds1, UD2 (user), PowS, PowS_MeasPointV/I3ph, Rec_FaultRecorder, Ln1_21DistanceProt1, Ln1_8521PermOverr, etc.

#### LN Classes únicas (las más extensas de todos los archivos)
CALH, CHCC, CILO, CSWI, GAPC, LCCH, LLN0, LPDI, LPDO, LPHD, LTIM, LTMS, LTRK, MMTR, MMXN, MMXU, MSQI, PDIF, PDIS, PHAR, PIOC, PSCH, PTOC, PTOV, PTRC, PTUV, RDRE, RFLO, ROPA, ROPD, ROPV, RPSB, RREC, RSOF, RSSR, RSYN, RTPC, RTRE, SADC, SBWI, SFFM, SPSQ, SSUM, SSVS, SSYM, TCTR, TVTR, USER, XCBR, XSWI, ZAXN, ZLIN (52 clases)

**Clases exclusivas (no vistas en otros):** CHCC, LPDI, LPDO, ROPA, ROPD, ROPV, RSOF, RTPC, RTRE, SADC, SBWI, SFFM, SPSQ, SSUM, SSVS, SSYM, USER, ZAXN

#### FCs presentes
ST, MX, CO, CF, DC, EX, SP, SE, SV, BL, OR, SR

#### Comunicación (GOOSE)
- 6 GSE publishers por IED (distintos LDs de salida)

---

### 2.13 Siemens_7UT85.cid (SCD – 2 IEDs)

**IEDs:** B02_R22_F003 (7UT85 – Transformer Differential) + B02_W12_D002 (6MD85)

#### Logical Devices (7UT85 - muestra)
AnUn_MeasTransdIn, Application, CB1/CB2/CB3, CB1_25Synchronization, CB1_FdOMV, PTD1 (87T), PTE1 (50N51N), PTS1/PTS2 (27UV, 51N, 51, 67), PowS, Rec_FaultRecorder, UD1/UD2 (usuario)

#### LN Classes únicas
CALH, CILO, CSWI, GAPC, LCCH, LLN0, LPHD, LTIM, LTMS, LTRK, MMXN, MMXU, MSQI, PDIF, PHAR, PTOC, PTRC, PTTR, PTUV, RDRE, ROPA, RSSR, RSYN, SADC, SBWI, SFFM, SPSQ, SSUM, SSYM, TCTR, TGSN, TVTR, USER, XCBR, YPTR, ZAXN (36 clases)

**Clases exclusivas:** TGSN (temperatura), YPTR (transformador de potencia), ZAXN.

#### FCs presentes
ST, MX, CO, CF, DC, EX, SE, SV, BL, SR

**Destaca:** FC=SE (Setting Edit), FC=SV (Substitution), FC=BL (Blocking). No tiene FC=SP.

#### Comunicación (GOOSE)
- 3 GSE publishers: `UD1/Control_DataSet`, `UD1/Control_DataSet_1`, `UD2/Control_DataSet`

---

### 2.14 Siprotec_7UM85.icd / Siprotec_7UM85_CID.icd

**IED:** TEMPLATE | SIEMENS | 7UM85 (Motor Protection) V08.30.06

Ambos archivos son prácticamente idénticos (CID vs ICD template), con 1 línea de diferencia.

#### Logical Devices
| inst | desc |
|------|------|
| CTRL | Control |
| DR | Fault recorder |
| MEAS | Measurement |
| PROT | Protection |
| UD1 | Señales GOOSE |

#### LN Classes únicas
CALH, CILO, CSWI, GAPC, LCCH, LLN0, LPHD, LTIM, LTRK, MMXN, MMXU, MSQI, PDIS, PDOP, PDUP, PFRC, PTOC, PTOF, PTOV, PTRC, PTUF, PTUV, PVOC, PVPH, RCLP, RDRE, ROPA, RSSR, SADC, SBWI, SFFM, SPSQ, SSUM, SSYM, TCTR, TVTR, XCBR, ZAXN (38 clases)

**Clases exclusivas (motor protection):** PDOP/PDUP (desequilibrio/sobrecarga), PVOC (sobreintensidad de tierra), PVPH (protección de falta de fase), RCLP (control de maniobra), PFRC (frecuencia).

#### Enumeraciones Siemens (nomenclatura extendida)
Siemens usa IDs con versión: `SIPROTEC5_Textlist_Health_V08.30.06_V07.90.00`, `SIPROTEC5_EnType_SIUnit_V08.30.06_V08.00.00` (108 unidades), `SIPROTEC5_Textlist_ctlModel_V08.30.06_V07.90.00`, etc. Muchas son enumeraciones propietarias de Siemens (SwitchingMode, SwitchingAuthority, etc.).

#### FCs presentes
ST, MX, CO, CF, DC, EX, SG, SV

**Destaca:** FC=SG (Setting Group read/write) — grupos de ajuste.

#### Comunicación (GOOSE)
- 1 GSE publisher: `UD1/Control_DataSet`

---

### 2.15 TRAfo_1.cid

**IED:** IED002 | NRR | PCS-221S (Transformador) v1.00

#### Logical Devices
| inst | desc |
|------|------|
| LD0 | General |
| MU01 | Merging Unit |
| RCD | Recorder |
| MUGO | GOOSE output |
| MUSV | SMV output |

#### LN Classes únicas
GGIO, LCCH, LGOS, LLN0, LPHD, PTRC, RDRE (7 clases — igual que MU1.1.cid pero sin TCTR/TVTR visibles en LN list)

#### FCs presentes
ST, MX, CO, CF, DC, EX, SP, SE, SV, BL, OR

#### Comunicación
- 1 GSE publisher (LD0/gocb0)

---

### 2.16 ZIV_UC.CID

**IED:** TBOCFQ05I01 | ZIV | #MCVA_N616M (Equipo de Control Multifuncion) v2.0

#### Logical Devices
| inst | desc |
|------|------|
| LD1 | Dispositivo lógico único |

#### LN Classes únicas
CALH, CILO, CSWI, GAPC, GGIO, GLOG, IHMI, LCCH, LLN0, LNTP, LPHD, MCXL, MHAI, MMTR, MMXU, MSQI, MSTA, RFTL, RFTT, SCBC, SIMG, TCTR, TVTR, XCBR, XSWI, ZBAT (26 clases)

**Clases exclusivas de ZIV:** GLOG (General Log), IHMI (HMI), LNTP (LN Type), MCXL (xcarga especial), RFTL/RFTT (teleprotección ZIV), SCBC (Bay Controller), SIMG (imagen de subestación), ZBAT (batería).

#### Enumeraciones adicionales
| EnumType | Descripción |
|----------|-------------|
| AutoMod_ziv_0 | High/Low/High-Low (ZIV-specific) |
| CalcMthd_ziv_0 | PRES/MIN/MAX/TOTMIN/TOTMAX/AVG/SDV/MIN-MAX... |
| cmdQual_ziv_0 | pulse/persistent |
| ColChk_ziv_0 | No/En un estado/En los dos estados |
| ConCls_ziv_0 | NONE/RS/RT |
| Dbpos_ziv_0 | 4 estados Dbpos |
| SIUnit | Completo (75 unidades) |
| multiplier | y(-24)..P(15) |
| PhyHealth_ziv_0, RSRTSel_ziv_0, VLSel_ziv_0, SynClc_ziv_0, LoadMod_ziv_0, InTranTyp_ziv_0 | Propietarias ZIV |

#### FCs presentes
ST, MX, CO, CF, DC, EX, SP

#### Comunicación (GOOSE)
- 3 GSE publishers en LD1: gcb01, gcb02, gcb03

---

### 2.17 proyecto_final_scd.scd

**IEDs:** LTVHA_ESPBO + LTPBO_ESVHA | SIEMENS | 7SL87 V08.83.04

Versión proyecto final (SCD) de los mismos IEDs en LTPBO_ESVHA.cid / LTVHA_ESPBO.cid. Idéntica estructura de LN Classes (24 clases) y FCs.

---

## 3. Patrones comunes entre archivos

### 3A. LN Classes — frecuencia de aparición

| LN Class | Archivos | Categoría | Descripción |
|----------|----------|-----------|-------------|
| LLN0 | TODOS | Información | Logical Node Zero (obligatorio) |
| LPHD | TODOS | Información | Physical Device |
| GGIO | 12/22 | Supervisión | Generic I/O (status, analogue) |
| XCBR | 10/22 | Control | Circuit Breaker |
| CSWI | 9/22 | Control | Switch Controller |
| RDRE | 9/22 | Supervisión | Disturbance Recorder |
| MMXU | 8/22 | Medición | 3-phase measurement |
| PTRC | 8/22 | Protección | Trip Conditioning |
| CILO | 8/22 | Control | Interlocking |
| MMTR | 7/22 | Medición | Energy Metering |
| MSQI | 7/22 | Medición | Sequence and Imbalance |
| LCCH | 6/22 | Información | Communication Channel |
| MMXN | 6/22 | Medición | Non-phase-related measurement |
| PDIS | 6/22 | Protección | Distance Protection |
| PTOC | 6/22 | Protección | Time Overcurrent |
| RDRE | 9/22 | Supervisión | Disturbance Records |
| GAPC | 6/22 | Automatización | Generic Automatic Process Control |
| TCTR | 6/22 | Medición | Current Transformer |
| TVTR | 6/22 | Medición | Voltage Transformer |
| XCBR | 10/22 | Control | Circuit Breaker |
| XSWI | 7/22 | Control | Switch |
| RFLO | 6/22 | Supervisión | Fault Locator |
| RBRF | 5/22 | Protección | Breaker Failure |
| PDIF | 5/22 | Protección | Differential Protection |
| LTRK | 5/22 | Información | Communication Link Tracking |
| LTIM | 5/22 | Información | Time Management |
| MHAI | 3/22 | Medición | Harmonics Analysis |
| MSTA | 3/22 | Medición | Metering Statistics |
| LGOS | 3/22 | Información | GOOSE Subscription |
| RSYN | 4/22 | Supervisión | Synchronism Check |
| RREC | 4/22 | Automatización | Auto-Reclosing |

**Clases menos frecuentes (1-2 archivos):**
- CBAY, CRSV (ABB), PPAM, PSOF, RFUF, RPSB (protección potencia), PHAR (armónico), PTOV/PTUV, PTTR (temperatura), RDIR, RPSB
- CALH, SADC, SBWI, SFFM, SPSQ, SSUM, SSYM (Siemens automation)
- ZLIN, ZAXN (impedancias)
- TGSN, YPTR (Siemens 7UT85 — transformador)
- PBAT, ZBAT, SIMG, GLOG, IHMI, MCXL, LNTP, RFTL, RFTT, SCBC (ZIV)
- PVOC, PVPH, PDOP, PDUP, RCLP (Siemens 7UM85 — motor)
- LGOS, LSVS (suscripción GOOSE/SMV)

### 3B. FCs más usados (todos los archivos)

| FC | Nombre | Presencia | Descripción |
|----|--------|-----------|-------------|
| ST | Status | TODOS | Estado (valores en tiempo real) |
| MX | Measurement | TODOS | Mediciones analógicas |
| CO | Control | TODOS | Operaciones de control |
| CF | Configuration | TODOS | Configuración |
| DC | Description | TODOS | Cadenas descriptivas |
| EX | Extended | TODOS | Extendido (vendor-specific) |
| SP | Setting/Parameter | 7/22 | Ajustes de parámetros (MICOM, ZIV, NRR) |
| SV | Substitution | 7/22 | Sustitución supervisada (Siemens, NRR) |
| SE | Setting Edit | 4/22 | Edición de grupos de ajuste (Siemens) |
| BL | Blocking | 4/22 | Bloqueo (Siemens, NRR) |
| OR | Originador | 3/22 | Categoría originador (NRR MU) |
| SR | Setting Read | 2/22 | Lectura de grupo activo (Siemens) |
| SG | Setting Group | 1/22 | Grupo de ajuste (Siprotec 7UM85) |

### 3C. Enumeraciones estándar comunes (todas las implementaciones)

| EnumType | Valores estándar |
|----------|-----------------|
| **ctlModel** | status-only(0), direct-with-normal-security(1), sbo-with-normal-security(2), direct-with-enhanced-security(3), sbo-with-enhanced-security(4) |
| **Mod / ModKind** | on(1), blocked(2), test(3), test/blocked(4), off(5) |
| **Beh / BehKind** | on(1), blocked(2), test(3), test/blocked(4), off(5) |
| **Health / HealthKind** | Ok(1), Warning(2), Alarm(3) |
| **orCategory** | not-supported(0)..process(8) |
| **dir / FaultDirectionKind** | unknown(0), forward(1), backward(2), both(3) |
| **CBOpCap** | None(1)..Close-Open-Close-Open(5) |
| **SwTyp** | Load Break(1), Disconnector(2), Earthing Switch(3), High Speed ES(4) |
| **SwOpCap** | None(1), Open(2), Close(3), Open and Close(4) |
| **seqT** | pos-neg-zero(0), dir-quad-zero(1) |
| **range** | normal(0), high(1), low(2), high-high(3), low-low(4) |
| **SIUnit** | 75+ unidades físicas (1=none, 5=A, 29=V, 33=Hz, 38=W, 61=VA, 63=VAr, 72=Wh, 73=VArh, etc.) |
| **multiplier** | y(-24)..E(18) con escalas métricas estándar |
| **AutoRecSt** | Ready(1), InProgress(2), Successful(3) [P143/P545 extienden a 11 valores] |
| **FltLoop** | PhaseAtoGround(1)..Others(7) |
| **Dbpos** | intermediate-state(0), off(1), on(2), bad-state(3) [estándar IEC 61850-7-3] |

---

## 4. Datos NO expuestos en IEDNavigator — análisis de gaps

### 4.1 FC=SP (Settings / Parámetros)

**Presente en:** MICOM P143/P545 (Schneider), ZIV_UC, LTPBO_ESVHA/LTVHA_ESPBO (Siemens 7SL87), MU1.1, TRAfo_1

**Cantidad de DAs SP:**
- MICOM P143: ~4 DAs SP (setVal INT32, BOOLEAN, ObjRef)
- ZIV_UC: ~34 DAs SP
- LTPBO_ESVHA/Siemens 7SL87: importante presencia

**Contenido:**
```xml
<DA name="setVal" fc="SP" dchg="true" bType="INT32" />
<DA name="setVal" fc="SP" dchg="true" bType="BOOLEAN" />
<DA name="setSrcRef" fc="SP" dchg="true" bType="ObjRef" />
```

**Gap:** IEDNavigator lee y muestra ST/MX pero **no expone FC=SP**. Los ajustes de protección (corrientes de arranque, tiempos, umbrales) son inaccesibles desde la GUI. Solo visibles en DataTypeTemplates pero no navegables.

### 4.2 FC=SE, SG (Setting Groups — Grupos de Ajuste)

**Presente en:** Siemens 7UT85, 7SL87, LTPBO_ESVHA/LTVHA_ESPBO, MU1.1, TRAfo_1 (FC=SE), Siprotec_7UM85 (FC=SG)

**Cantidad (Siemens 7UT85):** ~65 DAs con FC=SE

**Contenido:**
```xml
<DA fc="SE" name="setVal" bType="INT32" valKind="Set" />
<DA fc="SE" name="setMag" bType="Struct" type="AnalogValue_FLOAT32" />
<DA fc="SE" name="setCal" bType="Struct" type="CalendarTime" />
```

**Gap:** IEC 61850 define hasta 8 grupos de ajuste (setting groups). Los relés Siemens SIPROTEC5 los usan extensamente. IEDNavigator no tiene ninguna interfaz para:
- Seleccionar el grupo activo (SettingGroupControl)
- Editar grupos inactivos (FC=SE)
- Leer grupos en modo SG

### 4.3 FC=DC (Descriptions)

**Presente en:** TODOS los archivos

**Contenido típico:**
```xml
<DA name="d" fc="DC" bType="VisString255" />        <!-- descripción del DO -->
<DA name="vendor" fc="DC" bType="VisString255" />   <!-- fabricante -->
<DA name="swRev" fc="DC" bType="VisString255" />    <!-- versión firmware -->
<DA name="model" fc="DC" bType="VisString255" />    <!-- modelo hardware -->
```

**Gap:** IEDNavigator muestra los valores del árbol (ST/MX) pero no recupera ni muestra las cadenas DC de descripción junto a cada nodo. El usuario no ve la descripción textual de cada DA/DO.

### 4.4 FC=EX (Extended / Vendor-specific)

**Presente en:** TODOS los archivos

**Contenido típico:**
```xml
<DA name="ldNs" fc="EX" bType="VisString255">
    <Val>IEC 61850-7-4:2003</Val>
</DA>
<DA name="dataNs" fc="EX" bType="VisString255">
    <Val>$</Val>
</DA>
<DA name="configRev" fc="EX" ... />
```

**Gap:** FC=EX contiene namespace del estándar, revisión de configuración y otros metadatos vendor-specific. Frecuentemente readonly (valKind="RO"). No expuesto en GUI.

### 4.5 FC=SV (Substitution)

**Presente en:** ABB_RED670, LTPBO_ESVHA, Siemens_7UT85, Siemens_7SL87, MU1.1, TRAfo_1

**Contenido:**
```xml
<!-- DA con FC=SV permiten substituir un valor medido por un valor manual -->
<DA name="subVal" fc="SV" bType="..." />
<DA name="subQ"   fc="SV" bType="Quality" />
<DA name="subEna" fc="SV" bType="BOOLEAN" />
```

**Gap:** La sustitución de valores (simular un valor manualmente durante mantenimiento) es una función de seguridad de proceso que IEDNavigator no expone. Permite a un operador "congelar" un valor de proceso.

### 4.6 FC=BL (Blocking)

**Presente en:** LTPBO_ESVHA, Siemens_7SL87, MU1.1, TRAfo_1

**Contenido:**
```xml
<DA name="blkEna" fc="BL" bType="BOOLEAN" />
```

**Gap:** El bloqueo de valores (impedir actualizaciones de un atributo) no es visible ni operable en IEDNavigator.

### 4.7 Communication section (GSE / SMV addresses)

**Presente en:** La mayoría de archivos con GOOSE/SMV

**Contenido:**
```xml
<GSE ldInst="LD0" cbName="gcb1">
    <Address>
        <P type="MAC-Address">01-0C-CD-01-00-00</P>
        <P type="APPID">0001</P>
        <P type="VLAN-PRIORITY">4</P>
        <P type="VLAN-ID">000</P>
    </Address>
    <MinTime unit="s" multiplier="m">4</MinTime>
    <MaxTime unit="s" multiplier="m">10000</MaxTime>
</GSE>
<SMV ldInst="MUSV" cbName="smvcb0">
    <Address>
        <P type="MAC-Address">01-0C-CD-04-00-00</P>
        <P type="APPID">4000</P>
    </Address>
</SMV>
```

**Gap:** IEDNavigator muestra GoCBs en la GUI pero **no extrae las direcciones de la sección Communication del SCL** (MAC, APPID, VLAN-ID, VLAN-Priority, MinTime, MaxTime). El GOOSE publisher las usa internamente pero no las muestra al usuario para comparación/auditoría.

### 4.8 LGOS / LSVS — Nodos de suscripción

**LGOS (GOOSE Subscription) presente en:** MU1.1, TRAfo_1, ES-ENC.scd

**LSVS (Sampled Values Subscription) presente en:** Siemens_7SL87 (como SSVS - extended)

**Contenido LGOS:**
```xml
<LN lnClass="LGOS" ...>
    <!-- suscripción a GOOSE externo: quitado/conectado, calidad -->
    <DOI name="St">
        <DAI name="stVal" ... />  <!-- FC=ST -->
    </DOI>
</LN>
```

**Gap:** Los nodos LGOS indican qué GOOSE recibe el IED (suscripciones externas). IEDNavigator no visualiza el mapa de suscripciones GOOSE del servidor (qué publishers externos están configurados).

### 4.9 DataSets y su contenido FCDA

**Presente en:** Todos los archivos con ReportControl / GSEControl

**Ejemplo:**
```xml
<DataSet name="StatNrml">
    <FCDA ldInst="LD0" prefix="DR" lnClass="RDRE" lnInst="1" doName="MemUsedAlm" fc="ST" />
    <FCDA ldInst="LD0" prefix="SP" lnClass="GGIO" lnInst="1" doName="Ind" daName="stVal" fc="ST" />
</DataSet>
```

**Gap:** IEDNavigator muestra los DataSets en el modo servidor (editables en la tabla GOOSE). Sin embargo, en modo cliente no hay una vista que muestre qué DataSets existen en el IED remoto, cuántos FCDAs tienen y a qué atributos apuntan — lo cual es importante para saber qué viene en cada reporte/GOOSE.

### 4.10 SettingControl / Setting Groups (estructura SCL)

**Presente en:** ABB_CID (SettingControl numOfSGs="4"), LTPBO_ESVHA (SettingGroups con SGEdit)

```xml
<SettingControl numOfSGs="4" />
```

**Gap:** IEDNavigator no lee ni muestra el número de grupos de ajuste configurados ni permite seleccionar el grupo activo vía MMS (servicio `SelectActiveSettingGroup`).

---

## 5. Sugerencias de mejoras para IEDNavigator

### 5.1 Visualización de FC=SP (Settings Panel)

**Gap:** Los ajustes de protección (corrientes de arranque, temporización, umbrales) no son accesibles.

**Implementación sugerida:**
- En el árbol de modelo del cliente (`IEC61850Client`), incluir atributos con `Fc.SP` además de ST/MX.
- Agregar una pestaña "Ajustes (SP)" en el panel de detalles del LN seleccionado.
- Permitir escritura de SP mediante `association.setDataValues()` con `Fc.SP`.
- En `IEDExplorerApp.java`, agregar un filtro de FC en el árbol (checkbox: ST ☑ MX ☑ SP ☐ CF ☐).

**Código referencia:**
```java
// En IEC61850Client.java - readDataObject con FC=SP
List<FcModelNode> spNodes = new ArrayList<>();
for (ModelNode child : ln.getChildren()) {
    if (child instanceof FcModelNode) {
        FcModelNode fcNode = (FcModelNode) child;
        if (fcNode.getFc() == Fc.SP) spNodes.add(fcNode);
    }
}
association.getDataValues(spNode); // leer valor actual del setting
```

### 5.2 Soporte de Setting Groups (FC=SE / SG)

**Gap:** Relés Siemens SIPROTEC5 con múltiples grupos de ajuste (hasta 8) no gestionables.

**Implementación sugerida:**
- Agregar botón "Grupos de Ajuste" en la toolbar del modo cliente.
- Leer `LLN0.ActSG` (grupo activo), `LLN0.NumSG` (número total).
- Panel con selector de grupo + tabla de ajustes FC=SE del grupo seleccionado.
- `association.selectActiveSettingGroup(sgcb, groupNumber)` para cambiar grupo.

### 5.3 Panel de Descriptions (FC=DC)

**Gap:** Las cadenas descriptivas (vendor, model, swRev, d) no se muestran en el árbol.

**Implementación sugerida:**
- Al hacer clic en un LN/DO en el árbol del cliente, mostrar en un panel inferior los DAs con FC=DC.
- No requiere polling continuo (son valores estáticos).
- Permite al usuario ver: fabricante, modelo, revisión firmware, descripción funcional.

**Código referencia:**
```java
// Al seleccionar nodo en árbol - leer DC una vez
ModelNode namPlt = serverModel.findModelNode(lnRef + ".NamPlt", Fc.DC);
if (namPlt != null) {
    association.getDataValues((FcModelNode) namPlt);
    // mostrar vendor, model, swRev, d en panel de info
}
```

### 5.4 Visualización de FC=SV (Substitution)

**Gap:** La sustitución de valores (importante para mantenimiento) no es visible.

**Implementación sugerida:**
- En el menú contextual del árbol (click derecho sobre un nodo DO/DA), agregar "Ver sustitución".
- Mostrar subVal, subQ, subEna actuales.
- Opción "Habilitar sustitución" para forzar un valor específico.
- Requiere `association.setDataValues()` con `Fc.SV` y `subEna=true`.

### 5.5 Visualización de la sección Communication (GSE/SMV addresses)

**Gap:** Las direcciones MAC, APPID, VLAN de GOOSE/SMV no son visibles desde el SCL.

**Implementación sugerida:**
- En el parser SCL del servidor (`IEC61850Server.java` / `SclParser`), extraer la sección `<Communication>`.
- En el panel de GoCBs (pestaña GOOSE), agregar columnas: MAC Address, APPID, VLAN-ID, VLAN-Priority, MinTime, MaxTime.
- Facilita comparación con wireshark/configuración de switches de red.

### 5.6 Vista de Suscripciones GOOSE (LGOS viewer)

**Gap:** Los nodos LGOS que indican qué GOOSE external subscribe el IED no son navegables.

**Implementación sugerida:**
- En el árbol del modelo (cliente o servidor), identificar LNs con `lnClass=LGOS`.
- Para cada LGOS, mostrar: `GoRef` (qué GoCB se suscribe), `NdsCom` (necesidad de comunicación), `St.stVal`.
- Esto permite ver el mapa de dependencias GOOSE de un IED.

### 5.7 Explorador de DataSets (Dataset Browser)

**Gap:** No hay vista de los DataSets de un IED remoto y su contenido FCDA.

**Implementación sugerida:**
- En modo cliente, tras conexión, leer DataSets vía `association.getDataDirectory()`.
- Mostrar tabla: DataSet | FCDA count | Atributos (ldInst/LN.DO.DA [FC]).
- Click en DataSet expande la lista de FCDAs — permite entender qué datos vienen en cada reporte GOOSE.
- Usar `association.getDataSetDirectory(logicalDevice)` + `association.getDataSetValues(dataset)`.

### 5.8 Soporte completo de CtlModel (modos de control)

**Gap:** IEDNavigator actualmente soporta direct y SBO, pero no siempre lee correctamente el `ctlModel` del DA antes de decidir el tipo de operación.

**Implementación sugerida:**
- Antes de cualquier control, leer `DOI/Pos/ctlModel` (FC=CF) para determinar el modelo.
- Mostrar en la GUI el modelo de control para el usuario: "Control Directo (Seguridad Normal)" vs "SBO (Seguridad Mejorada)".
- Manejar explícitamente `direct-with-enhanced-security` (añade confirmación adicional).

### 5.9 Filtro de FC en árbol del modelo

**Gap:** El árbol muestra todos los DAs mezclados; ST/MX/CO/CF/DC/EX/SP sin distinción visual.

**Implementación sugerida:**
- En `IEDExplorerApp.java`, agregar panel de filtros de FC: checkboxes para ST, MX, CO, CF, DC, EX, SP, SE, SV.
- Color-coding por FC (ya existe por estado) — extender: rojo=ST alarm, azul=MX, naranja=SP, gris=DC.
- Permite al ingeniero encontrar rápidamente todos los ajustes SP de un relé.

### 5.10 Soporte de enumeraciones extendidas en formatValue()

**Gap:** `formatValue()` en `IEC61850Client.java` no decodifica todos los valores enum — muestra valores numéricos en vez de texto para enums no estándar.

**Implementación sugerida:**
- Cargar las `<EnumType>` del SCL en un mapa `id → Map<ord, String>`.
- Al formatear un `BdaInt8U` / `BdaInt32` con tipo Enum, buscar en el mapa para mostrar el texto.
- Afecta especialmente a: SIUnit (mostrar "V", "A", "Hz" en vez de "29", "5", "33"), multiplier, dir, FltLoop, AutoRecSt, range.

---

## Apéndice A — Tabla completa de LN Classes por archivo

| LN Class | ABB_CID | ABB_RED670 | BACE* | ES-ENC | LTPBO | MU1.1 | P143 | P545 | 7SL87 | 7UT85 | 7UM85 | SEL | ZIV | proj_final |
|----------|---------|-----------|-------|--------|-------|-------|------|------|-------|-------|-------|-----|-----|-----------|
| LLN0 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| LPHD | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| GGIO | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | — | — | ✓ | ✓ | — |
| XCBR | — | ✓ | — | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| XSWI | — | ✓ | — | ✓ | — | — | — | — | ✓ | — | — | ✓ | ✓ | — |
| CSWI | — | ✓ | — | ✓ | ✓ | — | — | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| CILO | — | ✓ | — | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| MMXU | — | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| MMXN | — | ✓ | — | ✓ | ✓ | — | — | — | ✓ | ✓ | ✓ | ✓ | — | ✓ |
| MMTR | — | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | — | — | ✓ | ✓ |
| MSQI | — | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| MHAI | — | — | ✓ | — | — | — | — | — | — | — | — | — | ✓ | — |
| MSTA | — | — | ✓ | — | — | — | ✓ | ✓ | — | — | — | — | ✓ | — |
| RDRE | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ |
| PTRC | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ |
| PTOC | — | ✓ | — | ✓ | — | — | ✓ | ✓ | ✓ | ✓ | ✓ | — | — | — |
| PDIS | — | ✓ | — | — | ✓ | — | — | ✓ | ✓ | — | ✓ | — | — | ✓ |
| PDIF | — | ✓ | — | — | — | — | ✓ | ✓ | ✓ | ✓ | — | ✓ | — | — |
| RBRF | — | ✓ | — | ✓ | ✓ | — | ✓ | ✓ | — | — | — | — | — | ✓ |
| RREC | — | ✓ | — | ✓ | — | — | ✓ | ✓ | ✓ | — | — | — | — | — |
| RSYN | — | ✓ | — | ✓ | — | — | ✓ | ✓ | ✓ | ✓ | — | — | — | — |
| RFLO | — | ✓ | — | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | — | — | — | ✓ |
| RDIR | — | ✓ | — | ✓ | — | — | — | — | — | — | — | — | — | — |
| PHAR | — | ✓ | — | — | — | — | — | — | ✓ | ✓ | — | — | — | — |
| PTOV | — | ✓ | — | — | — | — | ✓ | ✓ | ✓ | — | ✓ | — | — | — |
| PTUV | — | ✓ | — | — | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | — | — | — |
| PTTR | — | ✓ | — | — | — | — | ✓ | ✓ | — | ✓ | — | — | — | — |
| PFRC | — | — | — | — | — | — | ✓ | ✓ | — | — | ✓ | — | — | — |
| PSCH | — | ✓ | — | — | ✓ | — | — | ✓ | ✓ | — | — | — | — | — |
| LCCH | — | — | — | — | ✓ | ✓ | — | — | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| LGOS | — | — | — | ✓ | — | ✓ | — | — | — | — | — | — | — | — |
| TCTR | — | — | — | ✓ | ✓ | ✓ | — | — | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| TVTR | — | — | — | ✓ | ✓ | — | ✓ | ✓ | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| GAPC | — | — | — | — | ✓ | — | — | ✓ | ✓ | ✓ | ✓ | — | ✓ | — |
| CALH | — | — | — | — | — | — | — | ✓ | ✓ | ✓ | ✓ | — | ✓ | — |
| ZLIN | — | — | — | — | ✓ | — | — | — | ✓ | — | — | — | — | ✓ |

*BACE = BACEJQ01I08 + BACEJQ02I14 + cbo2

---

## Apéndice B — Resumen de Comunicación GOOSE/SMV

| Archivo | Tipo | IED | cbName / LD | MAC | APPID | VLAN | MinT | MaxT |
|---------|------|-----|-------------|-----|-------|------|------|------|
| ABB_CID.CID | GSE | FP1REF630 | LD0/gcb1 | 01-0C-CD-01-00-00 | 0001 | 000 | 4ms | 10000ms |
| ABB_RED670.cid | GSE×8 | AA2D1Q* | LD0/gcb_A | — | — | — | — | — |
| LTPBO_ESVHA.cid | GSE×4 | LTPBO+LTVHA | Ln1_8521.../CB1 | — | 0001-0004 | — | 10ms | 2000ms |
| MU1.1.cid | GSE | MU11 | LD0/gocb0 | — | — | — | — | — |
| MU1.1.cid | SMV | MU11 | MUSV/smvcb0 | — | — | — | — | — |
| TRAfo_1.cid | GSE | IED002 | LD0/gocb0 | — | — | — | — | — |
| Siemens_7SL87.cid | GSE×6 | B04_R14_F003 | PowS,QB1,QB9,Ds1,UD2×2 | — | — | — | — | — |
| Siemens_7UT85.cid | GSE×3 | B02_R22_F003 | UD1×2, UD2 | — | — | — | — | — |
| Siprotec_7UM85.icd | GSE | TEMPLATE | UD1/Control_DataSet | — | — | — | — | — |
| ZIV_UC.CID | GSE×3 | TBOCFQ05I01 | LD1/gcb01-03 | — | — | — | — | — |
| SEL_ARCH.CID | GSE | SEL487B_cid_1 | CFG/Goose_SEL | — | — | — | — | — |
| MICOM_P545_K4AED2.icd | GSE×8+ | TEMPLATE | System/gcb01-08 | — | — | — | — | — |
| ES-ENC.scd | GSE×4 | TR1-TR4 | PIGO/gocb0×4 | — | — | — | — | — |

---

*Fin del análisis — 22 archivos SCL procesados (20 con datos, 2 vacíos)*
