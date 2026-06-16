/*
 * Regression guard for the ISO/IEC 23634:2022 Annex H symbology identifier
 * (src/jabcode/symbology_id.h).
 *
 * Pins the NORMATIVE Table H.1 mapping permanently: every (ECI, FNC1) input
 * produces the exact "]j<m>" the standard prescribes. If anyone perturbs the
 * modifier table, this fails loudly.
 *
 * Build & run:  make -C src/jabcode test-symid
 */
#include <stdio.h>
#include <string.h>
#include "symbology_id.h"   /* the pure formatter under test */

struct row { int eci; int fnc1; const char* want; };

int main(void)
{
    /* The six rows of Annex H Table H.1, verbatim. */
    struct row rows[] = {
        {0, JAB_FNC1_NONE,      "]j0"},  /* no options                       */
        {1, JAB_FNC1_NONE,      "]j1"},  /* ECI implemented                  */
        {0, JAB_FNC1_PRECEDING, "]j2"},  /* FNC1 preceding 1st char          */
        {0, JAB_FNC1_FOLLOWING, "]j3"},  /* FNC1 after initial letter/digits */
        {1, JAB_FNC1_PRECEDING, "]j4"},  /* FNC1 preceding + ECI             */
        {1, JAB_FNC1_FOLLOWING, "]j5"},  /* FNC1 following + ECI             */
    };
    int n = (int)(sizeof(rows) / sizeof(rows[0])), fails = 0;
    char out[4];

    for (int i = 0; i < n; i++)
    {
        jab_format_symbology_identifier(rows[i].eci, rows[i].fnc1, out);
        int ok = (strcmp(out, rows[i].want) == 0);
        printf("  eci=%d fnc1=%d -> %s (want %s) %s\n",
               rows[i].eci, rows[i].fnc1, out, rows[i].want, ok ? "ok" : "FAIL");
        if (!ok) fails++;
    }

    /* The flags in use today (ECI/FNC1 not yet decoded): no ECI, no FNC1 -> ]j0 */
    jab_format_symbology_identifier(0, 0, out);
    int base_ok = (strcmp(out, "]j0") == 0);

    printf("symbology-id Table H.1: %d/%d rows correct; base=%s\n",
           n - fails, n, base_ok ? "]j0" : out);
    int pass = (fails == 0 && base_ok);
    printf("RESULT: %s\n", pass ? "PASS" : "FAIL");
    return pass ? 0 : 1;
}
