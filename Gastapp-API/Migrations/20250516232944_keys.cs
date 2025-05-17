using Microsoft.EntityFrameworkCore.Migrations;
using Npgsql.EntityFrameworkCore.PostgreSQL.Metadata;

#nullable disable

namespace Gastapp_API.Migrations
{
    /// <inheritdoc />
    public partial class keys : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_Spendings_Categories_CategoryId",
                table: "Spendings");

            migrationBuilder.DropPrimaryKey(
                name: "PK_Spendings",
                table: "Spendings");

            migrationBuilder.DropIndex(
                name: "IX_Spendings_CategoryId",
                table: "Spendings");

            migrationBuilder.DropPrimaryKey(
                name: "PK_Categories",
                table: "Categories");

            migrationBuilder.AlterColumn<int>(
                name: "SpendingId",
                table: "Spendings",
                type: "integer",
                nullable: false,
                oldClrType: typeof(int),
                oldType: "integer")
                .OldAnnotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn);

            migrationBuilder.AddColumn<int>(
                name: "CategoryId1",
                table: "Spendings",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddColumn<string>(
                name: "CategoryUserId",
                table: "Spendings",
                type: "text",
                nullable: false,
                defaultValue: "");

            migrationBuilder.AlterColumn<int>(
                name: "CategoryId",
                table: "Categories",
                type: "integer",
                nullable: false,
                oldClrType: typeof(int),
                oldType: "integer")
                .OldAnnotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn);

            migrationBuilder.AddPrimaryKey(
                name: "PK_Spendings",
                table: "Spendings",
                columns: new[] { "SpendingId", "UserId" });

            migrationBuilder.AddPrimaryKey(
                name: "PK_Categories",
                table: "Categories",
                columns: new[] { "CategoryId", "UserId" });

            migrationBuilder.CreateIndex(
                name: "IX_Spendings_CategoryId1_CategoryUserId",
                table: "Spendings",
                columns: new[] { "CategoryId1", "CategoryUserId" });

            migrationBuilder.AddForeignKey(
                name: "FK_Spendings_Categories_CategoryId1_CategoryUserId",
                table: "Spendings",
                columns: new[] { "CategoryId1", "CategoryUserId" },
                principalTable: "Categories",
                principalColumns: new[] { "CategoryId", "UserId" },
                onDelete: ReferentialAction.Cascade);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "FK_Spendings_Categories_CategoryId1_CategoryUserId",
                table: "Spendings");

            migrationBuilder.DropPrimaryKey(
                name: "PK_Spendings",
                table: "Spendings");

            migrationBuilder.DropIndex(
                name: "IX_Spendings_CategoryId1_CategoryUserId",
                table: "Spendings");

            migrationBuilder.DropPrimaryKey(
                name: "PK_Categories",
                table: "Categories");

            migrationBuilder.DropColumn(
                name: "CategoryId1",
                table: "Spendings");

            migrationBuilder.DropColumn(
                name: "CategoryUserId",
                table: "Spendings");

            migrationBuilder.AlterColumn<int>(
                name: "SpendingId",
                table: "Spendings",
                type: "integer",
                nullable: false,
                oldClrType: typeof(int),
                oldType: "integer")
                .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn);

            migrationBuilder.AlterColumn<int>(
                name: "CategoryId",
                table: "Categories",
                type: "integer",
                nullable: false,
                oldClrType: typeof(int),
                oldType: "integer")
                .Annotation("Npgsql:ValueGenerationStrategy", NpgsqlValueGenerationStrategy.IdentityByDefaultColumn);

            migrationBuilder.AddPrimaryKey(
                name: "PK_Spendings",
                table: "Spendings",
                column: "SpendingId");

            migrationBuilder.AddPrimaryKey(
                name: "PK_Categories",
                table: "Categories",
                column: "CategoryId");

            migrationBuilder.CreateIndex(
                name: "IX_Spendings_CategoryId",
                table: "Spendings",
                column: "CategoryId");

            migrationBuilder.AddForeignKey(
                name: "FK_Spendings_Categories_CategoryId",
                table: "Spendings",
                column: "CategoryId",
                principalTable: "Categories",
                principalColumn: "CategoryId",
                onDelete: ReferentialAction.Cascade);
        }
    }
}
