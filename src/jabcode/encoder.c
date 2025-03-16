/**
 * libjabcode - JABCode Encoding/Decoding Library
 *
 * Copyright 2016 by Fraunhofer SIT. All rights reserved.
 * See LICENSE file for full terms of use and distribution.
 *
 * Contact: Huajian Liu <liu@sit.fraunhofer.de>
 *			Waldemar Berchtold <waldemar.berchtold@sit.fraunhofer.de>
 *
 * @file encoder.c
 * @brief Symbol encoding
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include "jabcode.h"
#include "encoder.h"
#include "ldpc.h"
#include "detector.h"
#include "decoder.h"

/**
 * @brief Generate color palettes with more than 8 colors
 * @param color_number the number of colors
 * @param palette the color palette
*/
void genColorPalette(jab_int32 color_number, jab_byte* palette)
{
	if(color_number < 8)
		return ;

	jab_int32 vr, vg, vb;	//the number of variable colors for r, g, b channels
	switch(color_number)
	{
	case 16:
		vr = 4;
		vg = 2;
		vb = 2;
		break;
	case 32:
		vr = 4;
		vg = 4;
		vb = 2;
		break;
	case 64:
		vr = 4;
		vg = 4;
		vb = 4;
		break;
	case 128:
		vr = 8;
		vg = 4;
		vb = 4;
		break;
	case 256:
		vr = 8;
		vg = 8;
		vb = 4;
		break;
	default:
		return;
	}

	jab_float dr, dg, db;	//the pixel value interval for r, g, b channels
	dr = (vr - 1) == 3 ? 85 : 256 / (jab_float)(vr - 1);
	dg = (vg - 1) == 3 ? 85 : 256 / (jab_float)(vg - 1);
	db = (vb - 1) == 3 ? 85 : 256 / (jab_float)(vb - 1);

	jab_int32 r, g, b;		//pixel value
	jab_int32 index = 0;	//palette index
	for(jab_int32 i=0; i<vr; i++)
	{
		r = MIN((jab_int32)(dr * i), 255);
		for(jab_int32 j=0; j<vg; j++)
		{
			g = MIN((jab_int32)(dg * j), 255);
			for(jab_int32 k=0; k<vb; k++)
			{
				b = MIN((jab_int32)(db * k), 255);
				palette[index++] = (jab_byte)r;
				palette[index++] = (jab_byte)g;
				palette[index++] = (jab_byte)b;
			}
		}
	}
}

/**
 * @brief Set default color palette
 * @param color_number the number of colors
 * @param palette the color palette
 */
void setDefaultPalette(jab_int32 color_number, jab_byte* palette)
{
    if(color_number == 4)
    {
    	memcpy(palette + 0, jab_default_palette + FP0_CORE_COLOR * 3, 3);	//black   000 for 00
    	memcpy(palette + 3, jab_default_palette + 5 * 3, 3);				//magenta 101 for 01
    	memcpy(palette + 6, jab_default_palette + FP2_CORE_COLOR * 3, 3);	//yellow  110 for 10
    	memcpy(palette + 9, jab_default_palette + FP3_CORE_COLOR * 3, 3);	//cyan    011 for 11
    }
    else if(color_number == 8)
    {
        for(jab_int32 i=0; i<color_number*3; i++)
        {
            palette[i] = jab_default_palette[i];
        }
    }
    else
    {
    	genColorPalette(color_number, palette);
    }
}

/**
 * @brief Set default error correction levels
 * @param symbol_number the number of symbols
 * @param ecc_levels the ecc_level for each symbol
 */
void setDefaultEccLevels(jab_int32 symbol_number, jab_byte* ecc_levels)
{
    memset(ecc_levels, 0, symbol_number*sizeof(jab_byte));
}

/**
 * @brief Swap two integer elements
 * @param a the first element
 * @param b the second element
 */
void swap_int(jab_int32 * a, jab_int32 * b)
{
    jab_int32 temp=*a;
    *a=*b;
    *b=temp;
}

/**
 * @brief Swap two byte elements
 * @param a the first element
 * @param b the second element
 */
void swap_byte(jab_byte * a, jab_byte * b)
{
    jab_byte temp=*a;
    *a=*b;
    *b=temp;
}

/**
 * @brief Convert decimal to binary
 * @param dec the decimal value
 * @param bin the data in binary representation
 * @param start_position the position to write in encoded data array
 * @param length the length of the converted binary sequence
 */
void convert_dec_to_bin(jab_int32 dec, jab_char* bin, jab_int32 start_position, jab_int32 length)
{
    if(dec < 0) dec += 256;
    for (jab_int32 j=0; j<length; j++)
    {
        jab_int32 t = dec % 2;
        bin[start_position+length-1-j] = (jab_char)t;
        dec /= 2;
    }
}

/**
 * @brief Create encode object
 * @param color_number the number of module colors
 * @param symbol_number the number of symbols
 * @return the created encode parameter object | NULL: fatal error (out of memory)
 */
jab_encode* createEncode(jab_int32 color_number, jab_int32 symbol_number)
{
    jab_encode *enc;
    enc = (jab_encode *)calloc(1, sizeof(jab_encode));
    if(enc == NULL)
        return NULL;

    if(color_number != 4  && color_number != 8   && color_number != 16 &&
       color_number != 32 && color_number != 64 && color_number != 128 && color_number != 256)
    {
        color_number = DEFAULT_COLOR_NUMBER;
    }
    if(symbol_number < 1 || symbol_number > MAX_SYMBOL_NUMBER)
        symbol_number = DEFAULT_SYMBOL_NUMBER;

    enc->color_number 		 = color_number;
    enc->symbol_number 		 = symbol_number;
    enc->master_symbol_width = 0;
    enc->master_symbol_height= 0;
    enc->module_size 		 = DEFAULT_MODULE_SIZE;

    //set default color palette
	enc->palette = (jab_byte *)calloc(color_number * 3, sizeof(jab_byte));
    if(enc->palette == NULL)
    {
        reportError("Memory allocation for palette failed");
        return NULL;
    }
    setDefaultPalette(enc->color_number, enc->palette);
    //allocate memory for symbol versions
    enc->symbol_versions = (jab_vector2d *)calloc(symbol_number, sizeof(jab_vector2d));
    if(enc->symbol_versions == NULL)
    {
        reportError("Memory allocation for symbol versions failed");
        return NULL;
    }
    //set default error correction levels
    enc->symbol_ecc_levels = (jab_byte *)calloc(symbol_number, sizeof(jab_byte));
    if(enc->symbol_ecc_levels == NULL)
    {
        reportError("Memory allocation for ecc levels failed");
        return NULL;
    }
    setDefaultEccLevels(enc->symbol_number, enc->symbol_ecc_levels);
    //allocate memory for symbol positions
    enc->symbol_positions= (jab_int32 *)calloc(symbol_number, sizeof(jab_int32));
    if(enc->symbol_positions == NULL)
    {
        reportError("Memory allocation for symbol positions failed");
        return NULL;
    }
    //allocate memory for symbols
    enc->symbols = (jab_symbol *)calloc(symbol_number, sizeof(jab_symbol));
    if(enc->symbols == NULL)
    {
        reportError("Memory allocation for symbols failed");
        return NULL;
    }
    return enc;
}

/**
 * @brief Destroy encode object
 * @param enc the encode object
 */
void destroyEncode(jab_encode* enc)
{
    free(enc->palette);
    free(enc->symbol_versions);
    free(enc->symbol_ecc_levels);
    free(enc->symbol_positions);
    free(enc->bitmap);
    if(enc->symbols)
    {
        for(jab_int32 i=0; i<enc->symbol_number; i++)
        {
        	free(enc->symbols[i].data);
            free(enc->symbols[i].data_map);
            free(enc->symbols[i].metadata);
            free(enc->symbols[i].matrix);
        }
        free(enc->symbols);
    }
    free(enc);
}

/**
 * @brief Analyze the input data and determine the optimal encoding modes for each character
 * @param input the input character data
 * @param encoded_length the shortest encoding length
 * @return the optimal encoding sequence | NULL: fatal error (out of memory)
 */
jab_int32* analyzeInputData(jab_data* input, jab_int32* encoded_length)
{
    jab_int32 encode_seq_length=ENC_MAX;
    jab_char* seq = (jab_char *)malloc(sizeof(jab_char)*input->length);
    if(seq == NULL) {
        reportError("Memory allocation for sequence failed");
        return NULL;
    }
    jab_int32* curr_seq_len=(jab_int32 *)malloc(sizeof(jab_int32)*((input->length+2)*14));
    if(curr_seq_len == NULL){
        reportError("Memory allocation for current sequence length failed");
        free(seq);
        return NULL;
    }
    jab_int32* prev_mode=(jab_int32 *)malloc(sizeof(jab_int32)*(2*input->length+2)*14);
    if(prev_mode == NULL){
        reportError("Memory allocation for previous mode failed");
        free(seq);
        free(curr_seq_len);
        return NULL;
    }
    for (jab_int32 i=0; i < (2*input->length+2)*14; i++)
        prev_mode[i] = ENC_MAX/2;

    jab_int32* switch_mode = (jab_int32 *)malloc(sizeof(jab_int32) * 28);
    if(switch_mode == NULL){
        reportError("Memory allocation for mode switch failed");
        free(seq);
        free(curr_seq_len);
        free(prev_mode);
        return NULL;
    }
    for (jab_int32 i=0; i < 28; i++)
        switch_mode[i] = ENC_MAX/2;
    jab_int32* temp_switch_mode = (jab_int32 *)malloc(sizeof(jab_int32) * 28);
    if(temp_switch_mode == NULL){
        reportError("Memory allocation for mode switch failed");
        free(seq);
        free(curr_seq_len);
        free(prev_mode);
        free(switch_mode);
        return NULL;
    }
    for (jab_int32 i=0; i < 28; i++)
        temp_switch_mode[i] = ENC_MAX/2;

    //calculate the shortest encoding sequence
    //initialize start in upper case mode; no previous mode available
    for (jab_int32 k=0;k<7;k++)
    {
        curr_seq_len[k]=curr_seq_len[k+7]=ENC_MAX;
        prev_mode[k]=prev_mode[k+7]=ENC_MAX;
    }

    curr_seq_len[0]=0;
    jab_byte jp_to_nxt_char=0, confirm=0;
    jab_int32 curr_seq_counter=0;
    jab_boolean is_shift=0;
    jab_int32 nb_char=0;
    jab_int32 end_of_loop=input->length;
    jab_int32 prev_mode_index=0;
    for (jab_int32 i=0;i<end_of_loop;i++)
    {
        jab_int32 tmp=input->data[nb_char];
        jab_int32 tmp1=0;
        if(nb_char+1 < input->length)
            tmp1=input->data[nb_char+1];
        if(tmp<0)
            tmp=256+tmp;
        if(tmp1<0)
            tmp1=256+tmp1;
        curr_seq_counter++;
        for (jab_int32 j=0;j<JAB_ENCODING_MODES;j++)
        {
            if (jab_enconing_table[tmp][j]>-1 && jab_enconing_table[tmp][j]<64) //check if character is in encoding table
                curr_seq_len[(i+1)*14+j]=curr_seq_len[(i+1)*14+j+7]=character_size[j];
            else if((jab_enconing_table[tmp][j]==-18 && tmp1==10) || (jab_enconing_table[tmp][j]<-18 && tmp1==32))//read next character to decide if encodalbe in current mode
            {
                curr_seq_len[(i+1)*14+j]=curr_seq_len[(i+1)*14+j+7]=character_size[j];
                jp_to_nxt_char=1; //jump to next character
            }
            else //not encodable in this mode
                curr_seq_len[(i+1)*14+j]=curr_seq_len[(i+1)*14+j+7]=ENC_MAX;
        }
        curr_seq_len[(i+1)*14+6]=curr_seq_len[(i+1)*14+13]=character_size[6]; //input sequence can always be encoded by byte mode
        is_shift=0;
        for (jab_int32 j=0;j<14;j++)
        {
            jab_int32 prev=-1;
            jab_int32 len=curr_seq_len[(i+1)*14+j]+curr_seq_len[i*14+j]+latch_shift_to[j][j];
            prev_mode[curr_seq_counter*14+j]=j;
            for (jab_int32 k=0;k<14;k++)
            {
                if((len>=curr_seq_len[(i+1)*14+j]+curr_seq_len[i*14+k]+latch_shift_to[k][j] && k<13) || (k==13 && prev==j))
                {
                    len=curr_seq_len[(i+1)*14+j]+curr_seq_len[i*14+k]+latch_shift_to[k][j];
                    if (temp_switch_mode[2*k]==k)
                        prev_mode[curr_seq_counter*14+j]=temp_switch_mode[2*k+1];
                    else
                        prev_mode[curr_seq_counter*14+j]=k;
                    if (k==13 && prev==j)
                        prev=-1;
                }
            }
            curr_seq_len[(i+1)*14+j]=len;
            //shift back to mode if shift is used
            if (j>6)
            {
                if ((curr_seq_len[(i+1)*14+prev_mode[curr_seq_counter*14+j]]>len ||
                    (jp_to_nxt_char==1 && curr_seq_len[(i+1)*14+prev_mode[curr_seq_counter*14+j]]+character_size[(prev_mode[curr_seq_counter*14+j])%7]>len)) &&
                     j != 13)
                {
                    jab_int32 index=prev_mode[curr_seq_counter*14+j];
                    jab_int32 loop=1;
                    while (index>6 && curr_seq_counter-loop >= 0)
                    {
                        index=prev_mode[(curr_seq_counter-loop)*14+index];
                        loop++;
                    }

                    curr_seq_len[(i+1)*14+index]=len;
                    prev_mode[(curr_seq_counter+1)*14+index]=j;
                    switch_mode[2*index]=index;
                    switch_mode[2*index+1]=j;
                    is_shift=1;
                    if(jp_to_nxt_char==1 && j==11)
                    {
                        confirm=1;
                        prev_mode_index=index;
                    }
                }
                else if ((curr_seq_len[(i+1)*14+prev_mode[curr_seq_counter*14+j]]>len ||
                        (jp_to_nxt_char==1 && curr_seq_len[(i+1)*14+prev_mode[curr_seq_counter*14+j]]+character_size[prev_mode[curr_seq_counter*14+j]%7]>len)) && j == 13 )
                   {
                       curr_seq_len[(i+1)*14+prev_mode[curr_seq_counter*14+j]]=len;
                       prev_mode[(curr_seq_counter+1)*14+prev_mode[curr_seq_counter*14+j]]=j;
                       switch_mode[2*prev_mode[curr_seq_counter*14+j]]=prev_mode[curr_seq_counter*14+j];
                       switch_mode[2*prev_mode[curr_seq_counter*14+j]+1]=j;
                       is_shift=1;
                   }
                if(j!=13)
                    curr_seq_len[(i+1)*14+j]=ENC_MAX;
                else
                    prev=prev_mode[curr_seq_counter*14+j];
            }
        }
        for (jab_int32 j=0;j<28;j++)
        {
            temp_switch_mode[j]=switch_mode[j];
            switch_mode[j]=ENC_MAX/2;
        }

        if(jp_to_nxt_char==1 && confirm==1)
        {
            for (jab_int32 j=0;j<=2*JAB_ENCODING_MODES+1;j++)
            {
                if(j != prev_mode_index)
                    curr_seq_len[(i+1)*14+j]=ENC_MAX;
            }
            nb_char++;
            end_of_loop--;

        }
        jp_to_nxt_char=0;
        confirm=0;
        nb_char++;
    }

    //pick smallest number in last step
    jab_int32 current_mode=0;
    for (jab_int32 j=0;j<=2*JAB_ENCODING_MODES+1;j++)
    {
        if (encode_seq_length>curr_seq_len[(nb_char-(input->length-end_of_loop))*14+j])
        {
            encode_seq_length=curr_seq_len[(nb_char-(input->length-end_of_loop))*14+j];
            current_mode=j;
        }
    }
    if(current_mode>6)
        is_shift=1;
    if (is_shift && temp_switch_mode[2*current_mode+1]<14)
        current_mode=temp_switch_mode[2*current_mode+1];

    jab_int32* encode_seq = (jab_int32 *)malloc(sizeof(jab_int32) * (curr_seq_counter+1+is_shift));
    if(encode_seq == NULL)
    {
        reportError("Memory allocation for encode sequence failed");
        return NULL;
    }

    //check if byte mode is used more than 15 times in sequence
    //->>length will be increased by 13
    jab_int32 counter=0;
    jab_int32 seq_len=0;
	jab_int32 modeswitch=0;
    encode_seq[curr_seq_counter]=current_mode;//prev_mode[(curr_seq_counter)*14+current_mode];//prev_mode[(curr_seq_counter+is_shift-1)*14+current_mode];
    seq_len+=character_size[encode_seq[curr_seq_counter]%7];
    for (jab_int32 i=curr_seq_counter;i>0;i--)
    {
        if (encode_seq[i]==13 || encode_seq[i]==6)
            counter++;
        else
        {
            if(counter>15)
            {
                encode_seq_length+=13;
                seq_len+=13;

				//--------------------------------
				if(counter>8207) //2^13+15
				{
					if (encode_seq[i]==0 || encode_seq[i]==1 || encode_seq[i]==7 || encode_seq[i]==8)
						modeswitch=11;
					if (encode_seq[i]==2 || encode_seq[i]==9)
						modeswitch=10;
					if (encode_seq[i]==5 || encode_seq[i]==12)
						modeswitch=12;
					jab_int32 remain_in_byte_mode=counter/8207;
					jab_int32 remain_in_byte_mode_residual=counter%8207;
					encode_seq_length+=(remain_in_byte_mode) * modeswitch;
					seq_len+=(remain_in_byte_mode) * modeswitch;
					if(remain_in_byte_mode_residual<16)
					{
						encode_seq_length+=(remain_in_byte_mode-1) * 13;
						seq_len+=(remain_in_byte_mode-1) * 13;
					}
					else
					{
						encode_seq_length+=remain_in_byte_mode * 13;
						seq_len+=remain_in_byte_mode * 13;
					}
					if(remain_in_byte_mode_residual==0)
					{
						encode_seq_length-= modeswitch;
						seq_len-= modeswitch;
					}
				}
				//--------------------------------
				counter=0;
            }
        }
        if (encode_seq[i]<14 && i-1!=0)
        {
            encode_seq[i-1]=prev_mode[i*14+encode_seq[i]];
            seq_len+=character_size[encode_seq[i-1]%7];
            if(encode_seq[i-1]!=encode_seq[i])
                seq_len+=latch_shift_to[encode_seq[i-1]][encode_seq[i]];
        }
        else if (i-1==0)
        {
            encode_seq[i-1]=0;
            if(encode_seq[i-1]!=encode_seq[i])
                seq_len+=latch_shift_to[encode_seq[i-1]][encode_seq[i]];
            if(counter>15)
            {
                encode_seq_length+=13;
                seq_len+=13;

				//--------------------------------
				if(counter>8207) //2^13+15
				{
					modeswitch=11;
					jab_int32 remain_in_byte_mode=counter/8207;
					jab_int32 remain_in_byte_mode_residual=counter%8207;
					encode_seq_length+=remain_in_byte_mode * modeswitch;
					seq_len+=remain_in_byte_mode * modeswitch;
					if(remain_in_byte_mode_residual<16)
					{
						encode_seq_length+=(remain_in_byte_mode-1) * 13;
						seq_len+=(remain_in_byte_mode-1) * 13;
					}
					else
					{
						encode_seq_length+=remain_in_byte_mode * 13;
						seq_len+=remain_in_byte_mode * 13;
					}
					if(remain_in_byte_mode_residual==0)
					{
						encode_seq_length-= modeswitch;
						seq_len-= modeswitch;
					}
				}
				//--------------------------------
				counter=0;
            }
        }
        else
            return NULL;
    }
    *encoded_length=encode_seq_length;
    free(seq);
    free(curr_seq_len);
    free(prev_mode);
    free(switch_mode);
    free(temp_switch_mode);
    return encode_seq;
}

/**
 * @brief Check if master symbol shall be encoded in default mode
 * @param enc the encode parameters
 * @return JAB_SUCCESS | JAB_FAILURE
*/
jab_boolean isDefaultMode(jab_encode* enc)
{
	if(enc->color_number == 8 && (enc->symbol_ecc_levels[0] == 0 || enc->symbol_ecc_levels[0] == DEFAULT_ECC_LEVEL))
	{
		return JAB_SUCCESS;
	}
	return JAB_FAILURE;
}

/**
 * @brief Calculate the (encoded) metadata length
 * @param enc the encode parameters
 * @param index the symbol index
 * @return the metadata length (encoded length for master symbol)
*/
jab_int32 getMetadataLength(jab_encode* enc, jab_int32 index)
{
    jab_int32 length = 0;

    if (index == 0) //master symbol, the encoded length
    {
    	//default mode, no metadata
    	if(isDefaultMode(enc))
		{
			length = 0;
		}
		else
		{
			//Part I
			length += MASTER_METADATA_PART1_LENGTH;
			//Part II
			length += MASTER_METADATA_PART2_LENGTH;
		}
    }
    else //slave symbol, the original net length
    {
    	//Part I
        length += 2;
        //Part II
        jab_int32 host_index = enc->symbols[index].host;
        //V in Part II, compare symbol shape and size with host symbol
        if (enc->symbol_versions[index].x != enc->symbol_versions[host_index].x || enc->symbol_versions[index].y != enc->symbol_versions[host_index].y)
		{
			length += 5;
		}
        //E in Part II
        if (enc->symbol_ecc_levels[index] != enc->symbol_ecc_levels[host_index])
        {
            length += 6;
        }
    }
    return length;
}

/**
 * @brief Calculate the data capacity of a symbol
 * @param enc the encode parameters
 * @param index the symbol index
 * @return the data capacity
 */
jab_int32 getSymbolCapacity(jab_encode* enc, jab_int32 index)
{
	//number of modules for finder patterns
    jab_int32 nb_modules_fp;
    if(index == 0)	//master symbol
	{
		nb_modules_fp = 4 * 17;
	}
	else			//slave symbol
	{
		nb_modules_fp = 4 * 7;
	}
    //number of modules for color palette
    jab_int32 nb_modules_palette = enc->color_number > 64 ? (64-2)*COLOR_PALETTE_NUMBER : (enc->color_number-2)*COLOR_PALETTE_NUMBER;
	//number of modules for alignment pattern
	jab_int32 side_size_x = VERSION2SIZE(enc->symbol_versions[index].x);
	jab_int32 side_size_y = VERSION2SIZE(enc->symbol_versions[index].y);
	jab_int32 number_of_aps_x = jab_ap_num[enc->symbol_versions[index].x - 1];
	jab_int32 number_of_aps_y = jab_ap_num[enc->symbol_versions[index].y - 1];
	jab_int32 nb_modules_ap = (number_of_aps_x * number_of_aps_y - 4) * 7;
	//number of modules for metadata
	jab_int32 nb_of_bpm = log(enc->color_number)/log(2);
	jab_int32 nb_modules_metadata = 0;
	if(index == 0)	//master symbol
	{
		jab_int32 nb_metadata_bits = getMetadataLength(enc, index);
		if(nb_metadata_bits > 0)
		{
			nb_modules_metadata = (nb_metadata_bits - MASTER_METADATA_PART1_LENGTH) / nb_of_bpm; //only modules for PartII
			if((nb_metadata_bits - MASTER_METADATA_PART1_LENGTH) % nb_of_bpm != 0)
			{
				nb_modules_metadata++;
			}
			nb_modules_metadata += MASTER_METADATA_PART1_MODULE_NUMBER; //add modules for PartI
		}
	}
	jab_int32 capacity = (side_size_x*side_size_y - nb_modules_fp - nb_modules_ap - nb_modules_palette - nb_modules_metadata) * nb_of_bpm;
	return capacity;
}

/**
 * @brief Get the optimal error correction capability
 * @param capacity the symbol capacity
 * @param net_data_length the original data length
 * @param wcwr the LPDC parameters wc and wr
 * @return JAB_SUCCESS | JAB_FAILURE
 */
void getOptimalECC(jab_int32 capacity, jab_int32 net_data_length, jab_int32* wcwr)
{
	jab_float min = capacity;
	for (jab_int32 k=3; k<=6+2; k++)
	{
		for (jab_int32 j=k+1; j<=6+3; j++)
		{
			jab_int32 dist = (capacity/j)*j - (capacity/j)*k - net_data_length; //max_gross_payload = floor(capacity / wr) * wr
			if(dist<min && dist>=0)
			{
				wcwr[1] = j;
				wcwr[0] = k;
				min = dist;
			}
		}
	}
}
