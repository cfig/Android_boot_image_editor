'Struct3' formats

 | Format | C Type | Python type | Standard size | Type | Parameter | Size |
 | -- | -- | -- | -- | -- | -- | -- |
 | x | pad byte | no value |  | Type.Padding | "null,Byte,Int (only lower 8 bits are kept)" | 1 |
 | c | char | bytes of length 1 | 1 | kotlin.Char | "Character.class (only lower 8 bits are kept, higher 8 bits are discarded)" | 1 |
 | b | signed char | integer | 1 | kotlin.Byte | byte[] (item range: [-128~127]) | n |
 | s | char[] | bytes |  | kotlin.String | String.class | n |
 | B | unsigned char | integer | 1 | Kotlin.UByte | byte[] (item range: [0~255]) | n |
 | ? | _Bool | bool | 1 |  |  |  |
 | h | short | integer | 2 | kotlin.Short | "Int,Short, (range [-32768 , 32767])" | 2 |
 | H | unsigned short | integer | 2 | kotlin.UShort | "Int,Short,UShort,(range [0 , 65535])" | 2 |
 | i | int | integer | 4 | kotlin.Int | "[-2^31 , 2^31 - 1]" | 4 |
 | l | long | integer | 4 | kotlin.Int | "[-2^31 , 2^31 - 1]" | 4 |
 | I | unsigned int | integer | 4 | kotlin.UInt | "[0 , 2^32-1]" |  |
 | L | unsigned long | integer | 4 | kotlin.UInt | "[0 , 2^32-1]" |  |
 | q | long long | integer | 8 | kotlin.Long |  |  |  |
 | Q | unsigned long long | integer | 8 | kotlin.ULong |  |  |
 | e | (7) | float | 2 |  |  |  |
 | f | float | float | 4 |  |  |  |
 | d | double | float | 8 |  |  |  |
