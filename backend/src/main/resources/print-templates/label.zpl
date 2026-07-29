^XA
^FO50,50^BY2,3.0^BC^FD${barcode}^FS
^FO50,150^A0N,28,28^FD${productName}^FS
^FO50,190^A0N,14,14^FD${category}^FS
^FO50,280^A0N,18,18^FDCost: ₹${costPerUnit}/u^FS
^FO50,310^A0N,18,18^FDMRP: ₹${mrpPaise}^FS
^FO500,280^A0N,12,12^FDLot: ${lotId}^FS
<#if expiryDate?? && expiryDate != "">
^FO500,310^A0N,12,12^FDExp: ${expiryDate}^FS
</#if>
^FO50,400^A0N,12,12^FDRec: ${receivedDate}^FS
^XZ
