using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

#pragma warning disable CA1814 // Prefer jagged arrays over multidimensional

namespace Gastapp_API.Migrations
{
    /// <inheritdoc />
    public partial class initial2 : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.InsertData(
                table: "IncomeTypes",
                columns: new[] { "IncomeTypeId", "IncomeTypeName" },
                values: new object[,]
                {
                    { 1, "Semanal" },
                    { 2, "Quincenal" },
                    { 3, "Mensual" }
                });
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DeleteData(
                table: "IncomeTypes",
                keyColumn: "IncomeTypeId",
                keyValue: 1);

            migrationBuilder.DeleteData(
                table: "IncomeTypes",
                keyColumn: "IncomeTypeId",
                keyValue: 2);

            migrationBuilder.DeleteData(
                table: "IncomeTypes",
                keyColumn: "IncomeTypeId",
                keyValue: 3);
        }
    }
}
