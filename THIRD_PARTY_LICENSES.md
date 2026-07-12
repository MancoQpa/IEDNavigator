# Third-Party Licenses — IEDNavigator

IEDNavigator uses the following open-source libraries. Their licenses and copyright
notices are listed below as required by each respective license.

---

## 1. libiec61850 — MZ Automation GmbH
- **File**: `lib/iec61850.dll` (loaded at runtime via JNA)
- **License**: GNU General Public License v3 (GPLv3)
- **Copyright**: Copyright (C) MZ Automation GmbH
- **Source / Info**: https://libiec61850.com
- **Note**: This library is NOT bundled in the repository. Users must download
  and install it separately. See README.md → Prerequisites.

---

## 2. iec61850bean — beanit
- **File**: `lib/iec61850bean-1.9.0.jar`
- **License**: Apache License 2.0
- **Copyright**: Copyright (C) beanit contributors
- **Source**: https://github.com/beanit/iec61850bean

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at:
    http://www.apache.org/licenses/LICENSE-2.0
```

---

## 3. pcap4j — Kaito Yamada
- **Files**: `lib/pcap4j-core-1.8.2.jar`, `lib/pcap4j-packetfactory-static-1.8.2.jar`
- **License**: MIT License
- **Copyright**: Copyright (C) 2011-2019 Kaito Yamada
- **Source**: https://github.com/kaitoy/pcap4j

```
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
```

---

## 4. Java Native Access (JNA) — JNA Contributors
- **Files**: `lib/jna-5.14.0.jar`, `lib/jna-platform-5.14.0.jar`
- **License**: GNU Lesser General Public License v2.1 (LGPL-2.1) OR Apache License 2.0
- **Copyright**: Copyright (C) 2007-2023 Timothy Wall and JNA contributors
- **Source**: https://github.com/java-native-access/jna

```
Licensed under LGPL v2.1 or Apache License 2.0 at your option.
Full text: https://www.gnu.org/licenses/lgpl-2.1.html
```

---

## 5. FlatLaf — FormDev Software GmbH
- **File**: `lib/flatlaf-3.2.jar`
- **License**: Apache License 2.0
- **Copyright**: Copyright (C) 2019-2024 FormDev Software GmbH
- **Source**: https://github.com/JFormDesigner/FlatLaf

```
Licensed under the Apache License, Version 2.0.
http://www.apache.org/licenses/LICENSE-2.0
```

---

## 6. SLF4J — QOS.ch
- **Files**: `lib/slf4j-api-2.0.9.jar`, `lib/slf4j-simple-2.0.9.jar`
- **License**: MIT License
- **Copyright**: Copyright (C) 2004-2023 QOS.ch
- **Source**: https://www.slf4j.org

```
Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files, to deal
in the Software without restriction. THE SOFTWARE IS PROVIDED "AS IS".
```

---

## 7. asn1bean — beanit
- **File**: `lib/asn1bean-1.13.0.jar`
- **License**: Apache License 2.0
- **Copyright**: Copyright (C) beanit contributors
- **Source**: https://github.com/beanit/asn1bean

---

## 8. jASN1 — beanit
- **Files**: `lib/jasn1-1.11.3.jar`, `lib/jasn1-compiler-1.11.3.jar`
- **License**: Apache License 2.0
- **Copyright**: Copyright (C) beanit contributors
- **Source**: https://github.com/beanit/jasn1

---

## 9. ANTLR 2 — Terence Parr
- **File**: `lib/antlr-2.7.7.jar`
- **License**: BSD License
- **Copyright**: Copyright (C) 2003-2006 Terence Parr. All rights reserved.
- **Source**: https://www.antlr2.org

```
Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
1. Redistributions of source code must retain the above copyright notice.
2. Redistributions in binary form must reproduce the above copyright notice.
3. The name of the author may not be used to endorse or promote products
   derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR IMPLIED
WARRANTIES ARE DISCLAIMED.
```

---

## 10. Npcap — Nmap Project (external, not bundled)
- **License**: Npcap License (free for personal/educational use)
- **Required by**: pcap4j for Layer 2 packet capture on Windows
- **Download**: https://npcap.com
- **Note**: Npcap must be installed separately by the user. IEDNavigator does
  not bundle or distribute Npcap. Users are responsible for complying with
  the Npcap license terms.

---

*For the full Apache License 2.0 text see: https://www.apache.org/licenses/LICENSE-2.0*
*For the full GPL v3 text see: https://www.gnu.org/licenses/gpl-3.0.html*
*For the full LGPL v2.1 text see: https://www.gnu.org/licenses/lgpl-2.1.html*
