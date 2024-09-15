/* dracut-install.c  -- install files and executables

   Copyright (C) 2012 Harald Hoyer
   Copyright (C) 2012 Red Hat, Inc.  All rights reserved.

   This program is free software: you can redistribute it and/or modify
   under the terms of the GNU Lesser General Public License as published by
   the Free Software Foundation; either version 2.1 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public License
   along with this program; If not, see <http://www.gnu.org/licenses/>.
*/

#define PROGRAM_VERSION_STRING "1"

#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif

#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>

#define CPIO_END "TRAILER!!!"
#define CPIO_ENDLEN (sizeof(CPIO_END)-1)

static char buf[CPIO_ENDLEN * 2 + 1];

int main(int argc, char **argv)
{
        FILE *f;
        size_t s;

        if (argc != 2) {
                fprintf(stderr, "Usage: %s <file>\n", argv[0]);
                exit(1);
        }

        f = fopen(argv[1], "r");

        if (f == NULL) {
                fprintf(stderr, "Cannot open file '%s'\n", argv[1]);
                exit(1);
        }

        s = fread(buf, 6, 1, f);
        if (s <= 0) {
                fprintf(stderr, "Read error from file '%s'\n", argv[1]);
                fclose(f);
                exit(1);
        }
        fseek(f, 0, SEEK_SET);

        /* check, if this is a cpio archive */
        if (buf[0] == '0' && buf[1] == '7' && buf[2] == '0' && buf[3] == '7' && buf[4] == '0' && buf[5] == '1') {
                long pos = 0;

                /* Search for CPIO_END */
                do {
                        char *h;
                        fseek(f, pos, SEEK_SET);
                        buf[sizeof(buf) - 1] = 0;
                        s = fread(buf, CPIO_ENDLEN, 2, f);
                        if (s <= 0)
                                break;

                        h = strstr(buf, CPIO_END);
                        if (h) {
                                pos = (h - buf) + pos + CPIO_ENDLEN;
                                fseek(f, pos, SEEK_SET);
                                break;
                        }
                        pos += CPIO_ENDLEN;
                } while (!feof(f));

                if (feof(f)) {
                        /* CPIO_END not found, just cat the whole file */
                        fseek(f, 0, SEEK_SET);
                } else {
                        /* skip zeros */
                        while (!feof(f)) {
                                size_t i;

                                buf[sizeof(buf) - 1] = 0;
                                s = fread(buf, 1, sizeof(buf) - 1, f);
                                if (s <= 0)
                                        break;

                                for (i = 0; (i < s) && (buf[i] == 0); i++) ;

                                if (buf[i] != 0) {
                                        pos += i;
                                        fseek(f, pos, SEEK_SET);
                                        break;
                                }

                                pos += s;
                        }
                }
        }
        /* cat out the rest */
        while (!feof(f)) {
                s = fread(buf, 1, sizeof(buf), f);
                if (s <= 0)
                        break;

                s = fwrite(buf, 1, s, stdout);
                if (s <= 0)
                        break;
        }
        fclose(f);

        return EXIT_SUCCESS;
}
