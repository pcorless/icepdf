/*
 * Copyright 2026 Patrick Corless
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Provenance: ORIGINAL. Calls the OpenJPEG (BSD-2) public API only; no OpenJPEG code is copied.
 *
 * Reference decoder for the GH-613 fixtures. Unlike opj_decompress it applies no application-level
 * colour handling (no sYCC->RGB guess, no upsampling): the output is exactly what libopenjp2
 * returns, which for JP2 includes the library's own pclr/cmap and cdef handling.
 *
 * Usage: jpxref <in.j2k|in.jp2> <outPrefix> [reduce] [x0 y0 x1 y1]
 * Writes <outPrefix>.<c>.pgx per component: "PG ML <+|-> <prec> <w> <h>\n" then big-endian
 * samples, 1 byte for prec <= 8, 2 for <= 16, else 4.
 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "openjpeg.h"

static void quiet(const char *msg, void *data) { (void) msg; (void) data; }

static void err(const char *msg, void *data) { (void) data; fprintf(stderr, "[openjpeg] %s", msg); }

int main(int argc, char **argv) {
    if (argc != 3 && argc != 4 && argc != 8) {
        fprintf(stderr, "usage: jpxref in out [reduce] [x0 y0 x1 y1]\n");
        return 2;
    }
    FILE *f = fopen(argv[1], "rb");
    unsigned char sig[12];
    if (!f || fread(sig, 1, 12, f) < 2) { fprintf(stderr, "cannot read %s\n", argv[1]); return 1; }
    fclose(f);
    OPJ_CODEC_FORMAT fmt = (sig[0] == 0xFF && sig[1] == 0x4F) ? OPJ_CODEC_J2K : OPJ_CODEC_JP2;

    opj_codec_t *codec = opj_create_decompress(fmt);
    opj_set_info_handler(codec, quiet, NULL);
    opj_set_warning_handler(codec, quiet, NULL);
    opj_set_error_handler(codec, err, NULL);
    opj_dparameters_t params;
    opj_set_default_decoder_parameters(&params);
    if (argc >= 4) params.cp_reduce = (OPJ_UINT32) atoi(argv[3]);
    opj_stream_t *stream = opj_stream_create_default_file_stream(argv[1], OPJ_TRUE);
    opj_image_t *image = NULL;
    if (!opj_setup_decoder(codec, &params) || !opj_read_header(stream, codec, &image)) return 1;
    if (argc == 8 && !opj_set_decode_area(codec, image, atoi(argv[4]), atoi(argv[5]),
                                          atoi(argv[6]), atoi(argv[7]))) return 1;
    if (!opj_decode(codec, stream, image) || !opj_end_decompress(codec, stream)) return 1;

    for (OPJ_UINT32 c = 0; c < image->numcomps; c++) {
        opj_image_comp_t *comp = &image->comps[c];
        char name[4096];
        snprintf(name, sizeof name, "%s.%u.pgx", argv[2], c);
        FILE *o = fopen(name, "wb");
        if (!o) return 1;
        fprintf(o, "PG ML %c %u %u %u\n", comp->sgnd ? '-' : '+', comp->prec, comp->w, comp->h);
        int bytes = comp->prec <= 8 ? 1 : comp->prec <= 16 ? 2 : 4;
        size_t n = (size_t) comp->w * comp->h;
        for (size_t i = 0; i < n; i++) {
            OPJ_INT32 v = comp->data[i];
            for (int b = bytes - 1; b >= 0; b--) fputc((v >> (8 * b)) & 0xFF, o);
        }
        fclose(o);
    }
    printf("%u\n", image->numcomps);
    opj_image_destroy(image);
    opj_stream_destroy(stream);
    opj_destroy_codec(codec);
    return 0;
}
