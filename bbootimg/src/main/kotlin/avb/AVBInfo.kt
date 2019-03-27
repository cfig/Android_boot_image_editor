package avb

/*
    a wonderfaul base64 encoder/decoder: https://cryptii.com/base64-to-hex
 */
class AVBInfo(var header: Header? = null,
              var authBlob: AuthBlob? = null,
              var auxBlob: AuxBlob? = null,
              var footer: Footer? = null)
