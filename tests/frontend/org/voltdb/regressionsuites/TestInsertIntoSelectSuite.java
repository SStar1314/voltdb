/* This file is part of VoltDB.
 * Copyright (C) 2008-2014 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.voltdb.regressionsuites;

import java.io.IOException;

import org.voltdb.BackendTarget;
import org.voltdb.VoltTable;
import org.voltdb.client.Client;
import org.voltdb.client.ClientResponse;
import org.voltdb.client.NoConnectionsException;
import org.voltdb.client.ProcCallException;
import org.voltdb.compiler.VoltProjectBuilder;

public class TestInsertIntoSelectSuite extends RegressionSuite {

    public TestInsertIntoSelectSuite(String name) {
        super(name);
    }

    static final String vcDefault = "dachshund";
    static final long intDefault = 121;

    static public junit.framework.Test suite() {
        VoltServerConfig config = null;
        final MultiConfigSuiteBuilder builder = new MultiConfigSuiteBuilder(TestInsertIntoSelectSuite.class);

        final VoltProjectBuilder project = new VoltProjectBuilder();

        try {
            String schema =
                "CREATE TABLE target_p (bi bigint not null," +
                "vc varchar(100) default '" + vcDefault +"'," +
                "ii integer default " + intDefault + "," +
                "ti tinyint default " + intDefault + ");" +
                "partition table target_p on column bi;" +

                "CREATE TABLE source_p1 (bi bigint not null," +
                "vc varchar(100)," +
                "ii integer," +
                "ti tinyint);" +
                "partition table source_p1 on column bi;" +

                "CREATE TABLE source_p2 (bi bigint not null," +
                "vc varchar(100)," +
                "ii integer," +
                "ti tinyint);" +
                "partition table source_p2 on column bi;" +

                "CREATE TABLE source_r (bi bigint not null," +
                "vc varchar(4)," +
                "ii integer," +
                "ti tinyint);" +

                "create procedure insert_p_source_p as insert into target_p (bi, vc, ii, ti) select * from source_p1 where bi = ?;" +
                "partition procedure insert_p_source_p on table target_p column bi;" +

                "create procedure insert_p_use_defaults as insert into target_p (bi, ti) select bi, ti from source_p1 where bi = ?;" +
                "partition procedure insert_p_use_defaults on table target_p column bi;" +

                "create procedure insert_p_use_defaults_reorder as insert into target_p (ti, bi) select ti, bi from source_p1 where bi = ?;" +
                "partition procedure insert_p_use_defaults_reorder on table target_p column bi;" +

                "create procedure insert_p_source_p_agg as insert into target_p (bi, vc, ii, ti) " +
                "select bi, max(vc), max(ii), min(ti)" + " from source_p1 where bi = ? group by bi;" +
                "partition procedure insert_p_source_p_agg on table target_p column bi;" +

                // transpose ti, ii, columns so there are implicit integer->tinyint and tinyint->integer casts
                "create procedure insert_p_source_p_cast as insert into target_p (bi, vc, ti, ii) select * from source_p1 where bi = ?;" +
                "partition procedure insert_p_source_p_cast on table target_p column bi;" +

                // source_p2.ii contains values that will not fit into tinyint, so this procedure should throw an out-of-range conversion exception
                "create procedure insert_p_source_p_cast_out_of_range as " +
                "insert into target_p (bi, vc, ti, ii) " +
                "select * from source_p2 where bi = ?;" +
                "partition procedure insert_p_source_p_cast_out_of_range on table target_p column bi;" +

                // Implicit string->int and int->string conversion.
                "create procedure insert_p_source_p_nonsensical_cast as insert into target_p (bi, ii, vc, ti) select * from source_p1 where bi = ?;" +
                "partition procedure insert_p_source_p_nonsensical_cast on table target_p column bi;" +

                "create procedure select_and_insert_into_source as " +
                "insert into source_p1 (bi, vc, ti, ii) select bi, vc, ti, 1000 * ii from source_p1 where bi = ? order by bi, ti;" +
                "partition procedure select_and_insert_into_source on table source_p1 column bi;" +

                // HSQL seems to want a cast for the parameter
                // Note that there is no filter in source_r
                "create procedure insert_param_in_select_list as " +
                "insert into target_p (bi, vc, ii, ti) " +
                "select cast(? as bigint), vc, ii, ti from source_r order by ii;" +
                "partition procedure insert_param_in_select_list on table target_p column bi;" +

                "create procedure insert_wrong_partition as " +
                "insert into target_p (bi, ti) select ti, cast(? as tinyint) from source_r; " +
                "partition procedure insert_wrong_partition on table target_p column bi; " +

                "create procedure InsertIntoSelectWithJoin as " +
                "insert into target_p " +
                "select sp1.bi, sp1.vc, sp2.ii, sp2.ti " +
                "from source_p1 as sp1 inner join source_p2 as sp2 on sp1.bi = sp2.bi and sp1.ii = sp2.ii " +
                "where sp1.bi = ?;" +
                "partition procedure InsertIntoSelectWithJoin on table target_p column bi;" +
                "";
            project.addLiteralSchema(schema);
        } catch (IOException error) {
            fail(error.getMessage());
        }

        boolean success;

        // JNI
        config = new LocalCluster("iisf-onesite.jar", 1, 1, 0, BackendTarget.NATIVE_EE_JNI);
        success = config.compile(project);
        assertTrue(success);
        builder.addServerConfig(config);

        // CLUSTER (disable to opt for speed over coverage...
        config = new LocalCluster("iisf-cluster.jar", 2, 3, 1, BackendTarget.NATIVE_EE_JNI);
        success = config.compile(project);
        assertTrue(success);
        builder.addServerConfig(config);
        // ... disable for speed) */

        config = new LocalCluster("iisf-hsql.jar", 1, 1, 0, BackendTarget.HSQLDB_BACKEND);
        success = config.compile(project);
        assert(success);
        builder.addServerConfig(config);

        return builder;
    }

    private static void clearTables(Client client) throws Exception {
        ClientResponse resp = client.callProcedure("@AdHoc", "delete from source_p1");
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());

        resp = client.callProcedure("@AdHoc", "delete from source_p2");
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());

        resp = client.callProcedure("@AdHoc", "delete from target_p");
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
    }

    private static void initializeTables(Client client) throws Exception {

        ClientResponse resp = null;

        clearTables(client);

        for (int i=0; i < 10; i++) {

            resp = client.callProcedure("SOURCE_P1.insert", i, Long.toHexString(i), i, i);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_P1.insert", i, Long.toHexString(-i), -i, -i);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_P1.insert", i, Long.toHexString(i * 11), i * 11, i * 11);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_P1.insert", i, Long.toHexString(i * -11), i * -11, i * -11);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            int j = i + 5;

            resp = client.callProcedure("SOURCE_P2.insert", j, Long.toHexString(j), j, j);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_P2.insert", j, Long.toHexString(-j), -j, -j);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_P2.insert", j, Long.toHexString(j * 11), j * 11, (j * 11) % 128);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_P2.insert", j, Long.toHexString(j * -11), j * -11, -((j * 11) % 128));
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());


            resp = client.callProcedure("SOURCE_R.insert", j, Long.toHexString(j), j, j);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_R.insert", j, Long.toHexString(-j).substring(0, 3), -j, -j);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_R.insert", j, Long.toHexString(j * 11), j * 11, (j * 11) % 128);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());

            resp = client.callProcedure("SOURCE_R.insert", j, Long.toHexString(j * -11).substring(0, 3), j * -11, -((j * 11) % 128));
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        }
    }

  private static VoltTable getRows(Client client, String adHocQuery) throws NoConnectionsException, IOException, ProcCallException {
  ClientResponse resp = client.callProcedure("@AdHoc", adHocQuery);
  assertEquals(ClientResponse.SUCCESS, resp.getStatus());
  return resp.getResults()[0];
}

    public void testInsertIntoSelectAdHocFails() throws IOException {
        // for now only SP/SP is supported for insert-into-select
        verifyStmtFails(getClient(), "insert into target_p select * from source_p1", "only supported for single-partition stored procedures");
    }

    public void testPartitionedTableSimple() throws Exception
    {
        final Client client = getClient();
        ClientResponse resp;

        // Running the procedure with the first parameter (100) will cause 0 rows to be inserted
        // The second parameter (5) will insert 4 rows into the target table
        long[] params = new long[] {100, 5};
        String[] procs = new String[] {"insert_p_source_p", "insert_p_source_p_cast"};

        for (long param : params) {
            for (String proc : procs) {

                initializeTables(client);

                resp = client.callProcedure(proc, param);
                assertEquals(ClientResponse.SUCCESS, resp.getStatus());

                long numRowsInserted = resp.getResults()[0].asScalarLong();

                // verify that the corresponding rows in both tables are the same
                String selectAllSource = "select * from source_p1 where bi = " + param + " order by bi, ii";
                String selectAllTarget = "select * from target_p order by bi, ii";

                resp = client.callProcedure("@AdHoc", selectAllSource);
                assertEquals(ClientResponse.SUCCESS, resp.getStatus());
                VoltTable sourceRows = resp.getResults()[0];

                resp = client.callProcedure("@AdHoc", selectAllTarget);
                assertEquals(ClientResponse.SUCCESS, resp.getStatus());
                VoltTable targetRows = resp.getResults()[0];

                int i = 0;
                while(targetRows.advanceRow()) {
                    assertEquals(true, sourceRows.advanceRow());
                    assertEquals(sourceRows.getLong(0), targetRows.getLong(0));
                    assertEquals(sourceRows.getString(1), targetRows.getString(1));
                    assertEquals(sourceRows.getLong(2), targetRows.getLong(2));
                    assertEquals(sourceRows.getLong(3), targetRows.getLong(3));
                    i++;
                }

                assertEquals(numRowsInserted, i);
            }
        }
    }

    public void testSelectWithAggregation() throws Exception {
        final Client client = getClient();
        final long partitioningValue = 7;

        initializeTables(client);

        ClientResponse resp = client.callProcedure("insert_p_source_p_agg", partitioningValue);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        validateTableOfScalarLongs(resp.getResults()[0], new long[] {1});

        resp = client.callProcedure("@AdHoc", "select * from target_p order by bi");
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        VoltTable targetRows = resp.getResults()[0];

        assertTrue(targetRows.advanceRow());

        assertEquals(partitioningValue, targetRows.getLong(0));
        assertEquals(Long.toHexString(-partitioningValue), targetRows.getString(1));
        assertEquals(partitioningValue * 11, targetRows.getLong(2));
        assertEquals(partitioningValue * -11, targetRows.getLong(3));

        assertFalse(targetRows.advanceRow());
    }

    public void testOutOfRangeImplicitCasts() throws Exception {
        final Client client = getClient();
        final long partitioningValue = 14;

        initializeTables(client);

        verifyProcFails(client, "out of range", "insert_p_source_p_cast_out_of_range", partitioningValue);
    }

    public void testNonsensicalCasts() throws Exception {
        final Client client = getClient();
        final long partitioningValue = 5;

        initializeTables(client);

        verifyProcFails(client, "invalid character value",
                "insert_p_source_p_nonsensical_cast", partitioningValue);
    }

    public void testPartitionedTableWithSelectJoin() throws Exception
    {
        final Client client = getClient();
        initializeTables(client);

        // source_p1 contains 0..9
        // source_p2 contains 5..14

        final long partitioningValue = 7;
        ClientResponse resp = client.callProcedure("InsertIntoSelectWithJoin", partitioningValue);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        assertEquals(4, resp.getResults()[0].asScalarLong());

        String selectSp1 = "select * from source_p1 where bi = ? order by bi, ii";
        String selectSp2 = "select * from source_p2 where bi = ? order by bi, ii";
        String selectTarget = "select * from target_p order by bi, ii";

        resp = client.callProcedure("@AdHoc", selectTarget);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        VoltTable targetRows = resp.getResults()[0];

        resp = client.callProcedure("@AdHoc", selectSp1, partitioningValue);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        VoltTable sp1Rows = resp.getResults()[0];

        resp = client.callProcedure("@AdHoc", selectSp2, partitioningValue);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        VoltTable sp2Rows = resp.getResults()[0];

        while(targetRows.advanceRow()) {
                assertTrue(sp1Rows.advanceRow());
                assertTrue(sp2Rows.advanceRow());

                assertEquals(sp1Rows.getLong(0), targetRows.getLong(0));
                assertEquals(sp1Rows.getString(1), targetRows.getString(1));
                assertEquals(sp2Rows.getLong(2), targetRows.getLong(2));
                assertEquals(sp2Rows.getLong(3), targetRows.getLong(3));
        }
    }

    public void testInsertIntoSelectWithDefaults() throws Exception {
        final Client client = getClient();

        ClientResponse resp;
        long partitioningValue = 8;

        // Both inserts use the select to produce values only for a subset of columns.
        String[] procs = new String[] {"insert_p_use_defaults", "insert_p_use_defaults_reorder"};

        for (String proc : procs) {
            initializeTables(client);

            resp = client.callProcedure(proc, partitioningValue);
            validateTableOfScalarLongs(resp.getResults()[0], new long[] {4});

            String selectSp1 = "select * from source_p1 where bi = ? order by bi, ti";
            String selectTarget = "select * from target_p order by bi, ti";

            resp = client.callProcedure("@AdHoc", selectTarget);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());
            VoltTable targetRows = resp.getResults()[0];

            resp = client.callProcedure("@AdHoc", selectSp1, partitioningValue);
            assertEquals(ClientResponse.SUCCESS, resp.getStatus());
            VoltTable sp1Rows = resp.getResults()[0];

            while (targetRows.advanceRow()) {
                assertTrue(sp1Rows.advanceRow());

                assertEquals(sp1Rows.getLong(0), targetRows.getLong(0));
                assertEquals(vcDefault, targetRows.getString(1));
                assertEquals(intDefault, targetRows.getLong(2));
                assertEquals(sp1Rows.getLong(3), targetRows.getLong(3));
            }
            assertFalse(sp1Rows.advanceRow());
        }
    }

    public void testInsertIntoSelectSameTable() throws Exception {
        final Client client = getClient();
        initializeTables(client);

        final long partitioningValue = 3;
        ClientResponse resp = client.callProcedure("select_and_insert_into_source", partitioningValue);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        validateTableOfScalarLongs(resp.getResults()[0], new long[] {4});

        String selectOrigRows = "select * from source_p1 where bi = ? and abs(ii) < 1000 order by bi, ii";
        String selectNewRows = "select * from source_p1 where bi = ? and abs(ii) > 1000 order by bi, ii";

        resp = client.callProcedure("@AdHoc", selectOrigRows, partitioningValue);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        VoltTable origRows = resp.getResults()[0];

        resp = client.callProcedure("@AdHoc", selectNewRows, partitioningValue);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());
        VoltTable newRows = resp.getResults()[0];

        while (origRows.advanceRow()) {
            assertTrue(newRows.advanceRow());

            assertEquals(origRows.getLong(0), newRows.getLong(0));
            assertEquals(origRows.getString(1), newRows.getString(1));
            assertEquals(origRows.getLong(2) * 1000, newRows.getLong(2));
            assertEquals(origRows.getLong(3), newRows.getLong(3));

        }
        assertFalse(newRows.advanceRow());
    }

    public void testSelectListParam() throws Exception {
        final Client client = getClient();
        initializeTables(client);

        final long partitioningValue = 7;
        ClientResponse resp = client.callProcedure("insert_param_in_select_list", partitioningValue);
        assertEquals(ClientResponse.SUCCESS, resp.getStatus());

        // tables should be identical except for "bi"
        VoltTable sourceRows = getRows(client, "select * from source_r order by ii");
        VoltTable targetRows = getRows(client, "select * from target_p order by ii");

        //fail("target: " + targetRows);

        while (sourceRows.advanceRow()) {
            assertTrue(targetRows.advanceRow());

            assertEquals(partitioningValue, targetRows.getLong(0));
            assertEquals(sourceRows.getString(1), targetRows.getString(1));
            assertEquals(sourceRows.getLong(2), targetRows.getLong(2));
            assertEquals(sourceRows.getLong(3), targetRows.getLong(3));
        }
        assertFalse(targetRows.advanceRow());
    }

    public void testInsertWrongPartitionFails() throws Exception {

        if (m_config.getNodeCount() > 1) {
            Client client = getClient();
            initializeTables(client);

            final long partitioningValue = 9;
            verifyProcFails(client, "Mispartitioned tuple in single-partition insert statement.",
                    "insert_wrong_partition", partitioningValue);
        }
    }

}
