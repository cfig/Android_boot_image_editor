package avb

import avb.blob.AuthBlob
import avb.blob.AuxBlob
import avb.blob.Footer
import avb.blob.Header

/*
    a wonderfaul base64 encoder/decoder: https://cryptii.com/base64-to-hex
 */
@OptIn(ExperimentalUnsignedTypes::class)
class AVBInfo(var header: Header? = null,
              var authBlob: AuthBlob? = null,
              var auxBlob: AuxBlob? = null,
              var footer: Footer? = null)
